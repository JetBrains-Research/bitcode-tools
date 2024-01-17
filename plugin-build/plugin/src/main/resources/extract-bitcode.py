#!/usr/bin/env python3

import argparse
from collections import deque
from llvmlite import binding as llvm
import sys
from typing import List
from enum import Flag, auto

# usage: TODO
# example: ./bitcode-extract.py -i bitcode.ll -o extractedBitcode.ll -r 2 -func 'kfun:#main(kotlin.Array<kotlin.String>){}'


class ExtractBitcodeError(Exception):
    pass


def extract(target_func_name: str, rec_depth: int, bitcode: str) -> List[str]:
    """Extracts specified symbols from the @bitcode."""
    mod = parse(bitcode)
    # faster and more reliable than calling mod.get_function(name) each time
    functions = {func.name: func for func in mod.functions}

    if target_func_name not in functions:
        raise ExtractBitcodeError(
            f"no function with the name '{target_func_name}' was found")
    extracted_global_vars = {target_func_name: functions[target_func_name]}

    work_queue = deque(
        [global_var for global_var in extracted_global_vars.values()
         if global_var.is_function]
    )
    for _ in range(rec_depth):
        if len(work_queue) == 0:
            break
        func = work_queue.popleft()
        for block in func.blocks:
            for instr in block.instructions:
                if instr.opcode != 'call':
                    continue
                called_func_name = list(instr.operands)[-1].name
                if called_func_name in extracted_global_vars:
                    continue
                print(called_func_name)  # TODO: support logging by flag
                called_func = functions[called_func_name]
                extracted_global_vars[called_func_name] = called_func
                work_queue.append(called_func)

    return [str(global_var) for global_var in extracted_global_vars.values()]


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
    try:
        run_tool(args)
    except Exception as e:
        print(f"Failed with error: {e}")
        sys.exit(1)


main()
