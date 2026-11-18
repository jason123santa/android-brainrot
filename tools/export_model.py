#!/usr/bin/env python3
"""Export the local HF Llama to the dependency-free Java runtime format."""
import argparse
import json
import struct
from pathlib import Path

import numpy as np
from safetensors import safe_open


def byte_alphabet():
    # Canonical GPT-2 bytes_to_unicode, inverted.
    values = list(range(33, 127)) + list(range(161, 173)) + list(range(174, 256))
    chars = list(values)
    n = 0
    for b in range(256):
        if b not in values:
            values.append(b)
            chars.append(256 + n)
            n += 1
    return dict(zip(map(chr, chars), values))


def export(source, destination):
    config = json.loads((source / 'config.json').read_text())
    tokenizer = json.loads((source / 'tokenizer.json').read_text())
    assert config['model_type'] == 'llama'
    assert config['tie_word_embeddings'], 'Runtime requires tied embeddings'
    assert config['hidden_act'] == 'silu'
    assert not config.get('attention_bias', False) and not config.get('mlp_bias', False)
    rope = config.get('rope_parameters', {})
    assert rope.get('rope_type', 'default') == 'default' and not config.get('rope_scaling')
    assert tokenizer['model']['type'] == 'BPE'
    assert tokenizer['normalizer'] is None
    assert tokenizer['pre_tokenizer'] == {'type': 'Sequence', 'pretokenizers': [
        {'type': 'Digits', 'individual_digits': True},
        {'type': 'ByteLevel', 'add_prefix_space': False, 'trim_offsets': True, 'use_regex': True}]}
    vocab = tokenizer['model']['vocab']
    special = {t['id'] for t in tokenizer['added_tokens'] if t['special']}
    assert special == set(range(9)), 'Review special token handling for this tokenizer'
    ids = sorted(vocab, key=vocab.get)
    assert [vocab[t] for t in ids] == list(range(config['vocab_size']))
    reverse = byte_alphabet()
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open('wb') as out, safe_open(source / 'model.safetensors', framework='numpy') as tensors:
        def ints(*values):
            out.write(struct.pack('>' + 'i' * len(values), *values))

        def floats(values):
            out.write(np.asarray(values, dtype='>f4').tobytes())

        def matrix(name):
            w = tensors.get_tensor(name).astype(np.float32)
            scale = np.maximum(np.max(np.abs(w), axis=1) / 127.0, 1e-30)
            quant = np.clip(np.rint(w / scale[:, None]), -127, 127).astype(np.int8)
            floats(scale)
            out.write(quant.tobytes())

        out.write(b'BRJ1')
        ints(config['hidden_size'], config['intermediate_size'], config['num_hidden_layers'],
             config['num_attention_heads'], config['num_key_value_heads'], config['head_dim'],
             config['vocab_size'], config['max_position_embeddings'], len(special))
        floats([config['rms_norm_eps'], rope.get('rope_theta', config.get('rope_theta', 10000.0))])
        for i, token in enumerate(ids):
            data = token.encode('utf-8') if i in special else bytes(reverse[c] for c in token)
            ints(len(data))
            out.write(data)
        merges = tokenizer['model']['merges']
        ints(len(merges))
        for pair in merges:
            left, right = pair.split(' ') if isinstance(pair, str) else pair
            ints(vocab[left], vocab[right], vocab[left + right])
        matrix('model.embed_tokens.weight')
        for layer in range(config['num_hidden_layers']):
            prefix = 'model.layers.%d.' % layer
            floats(tensors.get_tensor(prefix + 'input_layernorm.weight'))
            for name in ('q_proj', 'k_proj', 'v_proj', 'o_proj'):
                matrix(prefix + 'self_attn.' + name + '.weight')
            floats(tensors.get_tensor(prefix + 'post_attention_layernorm.weight'))
            for name in ('gate_proj', 'up_proj', 'down_proj'):
                matrix(prefix + 'mlp.' + name + '.weight')
        floats(tensors.get_tensor('model.norm.weight'))
    print('%s: %.2f MiB, signed int8 rows + float32 scales' %
          (destination.name, destination.stat().st_size / 2**20))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True,
                        help='Directory containing the compatible HF checkpoint and tokenizer')
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'assets/model.bin')
    args = parser.parse_args()
    export(args.source, args.output)
