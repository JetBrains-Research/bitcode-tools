#!/usr/bin/env python3

import argparse
from collections import deque
from llvmlite import binding as llvm
import sys
from typing import List
from enum import Flag, auto
import logging

# usage: TODO
# example: ./bitcode-extract.py -i bitcode.ll -o extractedBitcode.ll -r 2 -func 'kfun:#main(kotlin.Array<kotlin.String>){}'


class ExtractBitcodeError(Exception):
    pass


def extract(target_func_name: str, max_rec_depth: int, bitcode: str) -> List[str]:
    """Extracts specified symbols from the @bitcode."""
    mod = parse(bitcode)
    # faster and more reliable than calling mod.get_function(name) each time
    functions = {func.name: func for func in mod.functions}

    if target_func_name not in functions:
        raise ExtractBitcodeError(
            f"no function with the name '{target_func_name}' was found")
    extracted_func_names = {target_func_name: 0}  # name: depth
    logging.info(f"found target function: '{target_func_name}'")

    work_queue = deque([func_name for func_name in extracted_func_names])
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
    requiredArgs.add_argument('-func', '--function',
                              help='Specify function to extract.', required=True)
    parser.add_argument('-r', '--recursive', type=nonnegative_int, default=0,
                        help=('Recursively extract all called functions at the specified depth. '
                              'Default depth is 0, meaning recursive extraction is disabled.'))
    parser.add_argument('-v', '--verbose', action='store_true',
                        help='Print extra info messages to track the extraction process.')

    args = parser.parse_args()
    return args


def run_tool(args: argparse.Namespace):
    with open(args.input, 'r') as input_file:
        bitcode = input_file.read()

    manager = LlvmNativeManager(mode=LlvmNativeManagerMode.ENABLE_PRINT)
    manager.initialize()
    extracted_symbols = extract(args.function, args.recursive, bitcode)
    manager.shutdown()  # must not be called in `finally` section due to Segmentation fault

    with open(args.output, 'w') as output_file:
        output_file.write('\n'.join(extracted_symbols))


def main():
    args = parse_args()
    if args.verbose:
        logging.basicConfig(level=logging.INFO,
                            format='%(levelname)s: %(message)s')
    else:
        logging.disable = True
    try:
        run_tool(args)
    except Exception as e:
        print(f"Failed with error: {e}")
        sys.exit(1)


main()
