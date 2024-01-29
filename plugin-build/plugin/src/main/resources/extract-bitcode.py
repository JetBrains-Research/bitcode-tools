#!/usr/bin/env python3

import argparse
from collections import deque
from enum import Flag, auto
from llvmlite import binding as llvm
import logging
import re
import sys
from typing import Callable, List, Dict, Optional, Set

# usage: check './bitcode-extract.py --help' or 'python3 bitcode-extract.py --help'

PRINT_IGNORED_FUNCTIONS_NAMES_MAX_LIMIT = 10


def compile_pattern(pattern: str) -> Optional[re.Pattern]:
    try:
        return re.compile(pattern)
    except Exception as e:
        logging.error(f"pattern '{pattern}' is invalid: {e}")
        return None


def find_matching_functions(
        patterns: Optional[List[str]],
        functions: Dict[str, llvm.ValueRef],
        handle_no_matches: Callable[[str], None],
        handle_matched_functions: Callable[[str, List[str]], List[str]]
) -> List[str]:
    if patterns is None:
        return []
    all_functions_names_matched = {}
    for pattern in dict.fromkeys(patterns):
        regex_pattern = compile_pattern(pattern)
        if regex_pattern is None:
            return []
        functions_names_matched = [
            func_name for func_name in functions if regex_pattern.match(func_name)]
        if len(functions_names_matched) == 0:
            handle_no_matches(pattern)
        else:
            all_functions_names_matched.update(dict.fromkeys(
                handle_matched_functions(pattern, functions_names_matched))
            )
    return all_functions_names_matched.keys()


def find_functions_to_ignore(
        functions_patterns_to_ignore: Optional[List[str]],
        functions: Dict[str, llvm.ValueRef]
) -> Set[str]:
    def handle_matched_functions(pattern_to_ignore: str, functions_names_matched: str) -> List[str]:
        functions_to_ignore_num = len(functions_names_matched)
        omit_functions_names = functions_to_ignore_num > PRINT_IGNORED_FUNCTIONS_NAMES_MAX_LIMIT
        functions_names_msg = ''.join(
            map(lambda func_name: f"\n\t'{func_name}'", functions_names_matched))
        if omit_functions_names:
            details_msg = " ... (their full list is omitted)"
        else:
            details_msg = functions_names_msg
        functions_word = "functions" if functions_to_ignore_num > 1 else "function"
        logging.info(
            f"matching pattern '{pattern_to_ignore}', {functions_to_ignore_num} {functions_word} will be ignored:{details_msg}")
        if omit_functions_names:
            logging.debug(
                f"omitted list:{functions_names_msg}")
        return functions_names_matched

    return set(find_matching_functions(
        functions_patterns_to_ignore,
        functions,
        handle_no_matches=lambda pattern_to_ignore: logging.warning(
            f"the ignoring pattern '{pattern_to_ignore}' is redundant, no functions matching it"),
        handle_matched_functions=handle_matched_functions
    ))


def find_target_functions(
    target_functions_names: Optional[List[str]],
    target_functions_patterns: Optional[List[str]],
    functions_to_ignore: Set[str],
    functions: Dict[str, llvm.ValueRef]
) -> Dict[str, int]:
    extracted_funcs_to_depths = {}  # name: depth

    if target_functions_names is not None:
        for target_func_name in dict.fromkeys(target_functions_names):
            if target_func_name not in functions:
                logging.warning(
                    f"no function with the name '{target_func_name}' was found")
            elif target_func_name in functions_to_ignore:
                logging.debug(
                    f"found target function by name, but it is ignored: '{target_func_name}'")
            else:
                extracted_funcs_to_depths[target_func_name] = 0
                logging.info(
                    f"found target function by name: '{target_func_name}'")

    def handle_matched_functions(target_func_pattern: str, functions_names_matched: str) -> List[str]:
        filtered_matched_functions = []
        for target_func_name in functions_names_matched:
            if target_func_name in functions_to_ignore:
                logging.debug(
                    f"matching pattern '{target_func_pattern}', found target function, but it is ignored: '{target_func_name}'")
            else:
                filtered_matched_functions.append(target_func_name)
                logging.info(
                    f"matching pattern '{target_func_pattern}', found target function: '{target_func_name}'")
        return filtered_matched_functions

    extracted_funcs_to_depths.update(dict.fromkeys(
        find_matching_functions(
            target_functions_patterns,
            functions,
            handle_no_matches=lambda target_func_pattern: logging.warning(
                f"no functions matching pattern '{target_func_pattern}' were found"),
            handle_matched_functions=handle_matched_functions
        ), 0)
    )

    return extracted_funcs_to_depths


def extract_function_calls_recursively(
        extracted_funcs_to_depths: Dict[str, int],
        max_rec_depth: int,
        functions_to_ignore: Set[str],
        functions: Dict[str, llvm.ValueRef]
) -> None:
    work_queue = deque(extracted_funcs_to_depths.keys())
    cur_depth = 0
    while True:
        if len(work_queue) == 0:
            logging.info(
                f"all functions calls have been found by recursion depth {cur_depth}\n")
            break
        func_name = work_queue.popleft()
        cur_depth = extracted_funcs_to_depths[func_name]
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
                if called_func_name in extracted_funcs_to_depths:
                    continue

                next_depth = cur_depth + 1
                if called_func_name in functions_to_ignore:
                    logging.debug(
                        f"found function call on recursion depth {next_depth}, but it is ignored: '{called_func_name}'")
                    continue

                extracted_funcs_to_depths[called_func_name] = next_depth
                work_queue.append(called_func_name)
                logging.info(
                    f"found function call on recursion depth {next_depth}: '{called_func_name}'")


def extract(
    target_functions_names: Optional[List[str]],
    target_functions_patterns: Optional[List[str]],
    functions_patterns_to_ignore: Optional[List[str]],
    max_rec_depth: int,
    bitcode: str
) -> List[str]:
    """Extracts specified symbols from the @bitcode."""
    mod = parse(bitcode)
    # faster and more reliable than calling mod.get_function(name) each time
    functions = {func.name: func for func in mod.functions}

    functions_to_ignore = find_functions_to_ignore(
        functions_patterns_to_ignore, functions)
    extracted_funcs_to_depths = find_target_functions(
        target_functions_names, target_functions_patterns, functions_to_ignore, functions)

    if len(extracted_funcs_to_depths) == 0:
        logging.warning("no functions to extract, output file will be empty\n")
        return []

    if max_rec_depth != 0:  # recursive extraction is enabled
        logging.info("----- (extract functions calls recursively)")
        extract_function_calls_recursively(
            extracted_funcs_to_depths, max_rec_depth, functions_to_ignore, functions)

    return [str(functions[func_name]) for func_name in extracted_funcs_to_depths]


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
    requiredArgs = parser.add_argument_group('required arguments')
    requiredArgs.add_argument(
        '-i', '--input', help='Input file path.', required=True)
    requiredArgs.add_argument(
        '-o', '--output', help='Output file path.', required=True)
    parser.add_argument('-f', '--function',
                              action='append',
                              help=('Specify function to extract by its name. '
                                    'To specify multiple functions use this flag several times.'))
    parser.add_argument('-fp', '--function-pattern',
                        action='append',
                        help=('Specify functions to extract by a regex pattern. '
                              'To provide multiple patterns use this flag several times.'))
    parser.add_argument('-ifp', '--ignore-function-pattern',
                        action='append',
                        help=('Specify functions to ignore by a regex pattern. '
                              'To provide multiple patterns use this flag several times.'))
    parser.add_argument('-r', '--recursion-depth', type=nonnegative_int, default=0,
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
        args.function,
        args.function_pattern,
        args.ignore_function_pattern,
        args.recursion_depth,
        bitcode
    )
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
