#!/usr/bin/env python3

import argparse
from collections import deque
from llvmlite import binding as llvm
import os
from typing import List

# usage: TODO
# example: ./bitcode-extract.py -i bitcode.ll -o extractedBitcode.ll -r 2 -func 'kfun:#main(kotlin.Array<kotlin.String>){}'


def extract(func_name: str, rec_depth: int, bitcode: str) -> List[str]:
    """Extracts specified symbols from the @bitcode."""
    mod = parse(bitcode)
    # faster and more reliable than calling mod.get_function(name) each time
    functions = {f.name: f for f in mod.functions}
    # extracted global variables
    gvs = {func_name: functions[func_name]}
    # TODO: handle func_name absence properly

    work_queue = deque([gv for _, gv in gvs.items() if gv.is_function])
    for _ in range(rec_depth):
        if len(work_queue) == 0:
            break
        f = work_queue.popleft()
        for b in f.blocks:
            for i in b.instructions:
                if i.opcode != 'call':
                    continue
                cf_name = list(i.operands)[-1].name
                if cf_name in gvs:
                    continue
                print(cf_name) # TODO: support logging by flag
                cf = functions[cf_name]
                gvs[cf_name] = cf
                work_queue.append(cf)

    return [str(gv) for _, gv in gvs.items()]


def parse(bitcode: str) -> llvm.ModuleRef:
    mod = llvm.parse_assembly(bitcode)
    mod.verify()
    return mod


class LlvmNativeManager():
    def __init__(self, codegen: bool = False, print: bool = False) -> None:
        self.codegen = codegen
        self.print = print

    def __enter__(self):
        llvm.initialize()
        if self.codegen:
            llvm.initialize_native_target()
        if self.print:
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

with LlvmNativeManager(print=True):
    extracted_symbols = extract(args.function, args.recursive, bitcode)

with open(args.output, 'w') as output_file:
    output_file.write('\n'.join(extracted_symbols))
