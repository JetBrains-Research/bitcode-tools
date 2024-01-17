#!/usr/bin/env python3

import argparse
from collections import deque
from llvmlite import binding as llvm
import os
from typing import List
from enum import Flag, auto

# usage: TODO
# example: ./bitcode-extract.py -i bitcode.ll -o extractedBitcode.ll -r 2 -func 'kfun:#main(kotlin.Array<kotlin.String>){}'


def extract(func_name: str, rec_depth: int, bitcode: str) -> List[str]:
    """Extracts specified symbols from the @bitcode."""
    mod = parse(bitcode)
    # faster and more reliable than calling mod.get_function(name) each time
    functions = {func.name: func for func in mod.functions}
    extracted_global_vars = {func_name: functions[func_name]}
    # TODO: handle func_name absence properly

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

    def __enter__(self):
        llvm.initialize()
        if self.mode in LlvmNativeManagerMode.ENABLE_PRINT_AND_CODEGEN:
            llvm.initialize_native_target()
        if self.mode in LlvmNativeManagerMode.ENABLE_PRINT | LlvmNativeManagerMode.ENABLE_PRINT_AND_CODEGEN:
            llvm.initialize_native_asmprinter()

    def __exit__(self, exc_type, exc_value, exc_traceback):
        llvm.shutdown()


def valid_bitcode_ll(value):
    _, ext = os.path.splitext(value)
    if ext.lower() != ('.ll') or not os.path.exists(value):
        raise argparse.ArgumentTypeError(
            f'{value} is not a bitcode `.ll` file')
    return value


def nonnegative_int(value):
    ivalue = int(value)
    if ivalue < 0:
        raise argparse.ArgumentTypeError(
            f'{value} is not a positive int value')
    return ivalue


parser = argparse.ArgumentParser()
requiredArgs = parser.add_argument_group('required named arguments')
requiredArgs.add_argument(
    '-i', '--input', help='Input file name.', type=valid_bitcode_ll, required=True)
requiredArgs.add_argument(
    '-o', '--output', help='Output file name.', required=True)
requiredArgs.add_argument('-func', '--function',
                          help='Specify function to extract.', required=True)
parser.add_argument('-r', '--recursive', type=nonnegative_int, default=0,
                    help=('Recursively extract all called functions at the specified depth. '
                          'Default depth is 0, meaning recursive extraction is disabled.'))

args = parser.parse_args()

with open(args.input, 'r') as input_file:
    bitcode = input_file.read()

with LlvmNativeManager(mode=LlvmNativeManagerMode.ENABLE_PRINT):
    extracted_symbols = extract(args.function, args.recursive, bitcode)

with open(args.output, 'w') as output_file:
    output_file.write('\n'.join(extracted_symbols))
