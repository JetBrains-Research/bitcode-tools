#!/usr/bin/env python3

import argparse
from collections import deque
from enum import Flag, auto
from llvmlite import binding as llvm
import logging
import re
import sys
from typing import List, Dict, Optional

# usage: TODO
# example: ./bitcode-extract.py -i bitcode.ll -o extractedBitcode.ll -r 2 -func 'kfun:#main(kotlin.Array<kotlin.String>){}'


def find_target_functions(
    target_functions_names: Optional[List[str]],
    target_functions_patterns: Optional[List[str]],
    functions: Dict[str, llvm.ValueRef]
) -> Dict[str, int]:
    extracted_func_names = {}  # name: depth

    if target_functions_names is not None:
        for target_func_name in dict.fromkeys(target_functions_names):
            if target_func_name not in functions:
                logging.warning(
                    f"no function with the name '{target_func_name}' was found")
            else:
                extracted_func_names[target_func_name] = 0
                logging.info(
                    f"found target function by name: '{target_func_name}'")

    if target_functions_patterns is not None:
        for target_func_pattern in dict.fromkeys(target_functions_patterns):
            try:
                regex_pattern = re.compile(target_func_pattern)
            except Exception as e:
                logging.error(f"pattern '{target_func_pattern}' is invalid: {e}")
                continue

            functions_names_matched = [
                func_name for func_name in functions if regex_pattern.match(func_name)]
            if len(functions_names_matched) == 0:
                logging.warning(
                    f"no functions matching the pattern '{target_func_pattern}' were found")
            else:
                extracted_func_names.update(
                    dict.fromkeys(functions_names_matched, 0))
                for func_name in functions_names_matched:
                    logging.info(
                        f"for the pattern '{target_func_pattern}', found target function: '{func_name}'")

    return extracted_func_names


def extract_function_calls_recursively(
        extracted_func_names: Dict[str, int],
        max_rec_depth: int,
        functions: Dict[str, llvm.ValueRef]
) -> None:
    work_queue = deque(extracted_func_names.keys())
    cur_depth = 0
    while True:
        if len(work_queue) == 0:
            logging.info(
                f"all functions calls have been found by recursion depth {cur_depth}\n")
            break
        func_name = work_queue.popleft()
        cur_depth = extracted_func_names[func_name]
        if cur_depth >= max_rec_depth:
            logging.info(
                f"function calls up to max recursion depth {max_rec_depth} have been found\n")
            break

        for block in functions[func_name].blocks:
            for instr in block.instructions:
                if instr.opcode != 'call':
                    continue

                called_operand = list(instr.operands)[-1]
                if called_operand.value_kind is not llvm.ValueKind.function:
                    continue

                called_func_name = called_operand.name
                if called_func_name in extracted_func_names:
                    continue

                extracted_func_names[called_func_name] = cur_depth + 1
                work_queue.append(called_func_name)
                logging.info(
                    f"found function call on recursion depth {extracted_func_names[called_func_name]}: '{called_func_name}'")


def extract(
    target_functions_names: List[str],
    target_functions_regexes: List[str],
    max_rec_depth: int,
    bitcode: str
) -> List[str]:
    """Extracts specified symbols from the @bitcode."""
    mod = parse(bitcode)
    # faster and more reliable than calling mod.get_function(name) each time
    functions = {func.name: func for func in mod.functions}

    extracted_func_names = find_target_functions(
        target_functions_names, target_functions_regexes, functions)  # name: depth
    if len(extracted_func_names) == 0:
        logging.warning("no functions to extract, output file will be empty\n")
        return []

    extract_function_calls_recursively(
        extracted_func_names, max_rec_depth, functions)

    return [str(functions[func_name]) for func_name in extracted_func_names]


def parse(bitcode: str) -> llvm.ModuleRef:
    mod = llvm.parse_assembly(bitcode)
    mod.verify()
    return mod


class LlvmNativeManagerMode(Flag):
    ONLY_INIT = auto()
    ENABLE_PRINT = auto()
    ENABLE_PRINT_AND_CODEGEN = auto()


class LlvmNativeManager():
    def __init__(self, mode: LlvmNativeManagerMode = LlvmNativeManagerMode.ONLY_INIT) -> None:
        self.mode = mode

    def initialize(self):
        llvm.initialize()
        if self.mode in LlvmNativeManagerMode.ENABLE_PRINT_AND_CODEGEN:
            llvm.initialize_native_target()
        if self.mode in LlvmNativeManagerMode.ENABLE_PRINT | LlvmNativeManagerMode.ENABLE_PRINT_AND_CODEGEN:
            llvm.initialize_native_asmprinter()

    def shutdown(self):
        llvm.shutdown()


def nonnegative_int(value):
    ivalue = int(value)
    if ivalue < 0:
        raise argparse.ArgumentTypeError(
            f'{value} is not a positive int value')
    return ivalue


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    requiredArgs = parser.add_argument_group('required named arguments')
    requiredArgs.add_argument(
        '-i', '--input', help='Input file path.', required=True)
    requiredArgs.add_argument(
        '-o', '--output', help='Output file path.', required=True)
    parser.add_argument('-f', '--function',
                              action='append',
                              help=('Specify function to extract by its name. '
                                    'To specify multiple functions use this flag several times.'))
    parser.add_argument('-p', '--function-pattern',
                              action='append',
                              help=('Specify functions to extract by a regex pattern. '
                                    'To provide multiple patterns use this flag several times.'))
    parser.add_argument('-r', '--recursive', type=nonnegative_int, default=0,
                        help=('Recursively extract all called functions at the specified depth. '
                              'Default depth is 0, meaning recursive extraction is disabled.'))
    parser.add_argument('-v', '--verbose', action='store_true',
                        help='Print extra info messages to track the extraction process.')

    args = parser.parse_args()
    if not (args.function or args.function_pattern):
        parser.error(
            "No action requested, at least one function to extract (by its name or a pattern) should be specified")

    return args


def run_tool(args: argparse.Namespace):
    with open(args.input, 'r') as input_file:
        bitcode = input_file.read()

    manager = LlvmNativeManager(mode=LlvmNativeManagerMode.ENABLE_PRINT)
    manager.initialize()
    extracted_symbols = extract(
        args.function, args.function_pattern, args.recursive, bitcode)
    manager.shutdown()  # must not be called in `finally` section due to Segmentation fault

    with open(args.output, 'w') as output_file:
        output_file.write('\n'.join(extracted_symbols))


def main():
    args = parse_args()

    logging_level = logging.INFO if args.verbose else logging.WARNING
    logging.basicConfig(level=logging_level,
                        format='[%(levelname)s] %(message)s')

    try:
        run_tool(args)
    except Exception as e:
        print(f"Failed with error: {e}")
        sys.exit(1)


main()
