#!/usr/bin/env python3
"""Convert a CS6013 test program to MiniJava: convert.py input.java > output.java

main's body moves to <Main>Body.run() (so one extra 0 is printed), for -> while,
a < b -> a <= (b - 1), obj.f -> get_f()/set_f(), call statements -> dummy locals.
"""
import re
import sys


def match_brace(s, i):
    """Index of the '}' matching the '{' at s[i]."""
    depth = 0
    for j in range(i, len(s)):
        if s[j] == '{':
            depth += 1
        elif s[j] == '}':
            depth -= 1
            if depth == 0:
                return j
    raise ValueError("unbalanced braces")


def classes(src):
    """[(name, parent, body_start, body_end)] for every class."""
    out = []
    for m in re.finditer(r'\bclass\s+(\w+)(?:\s+extends\s+(\w+))?\s*\{', src):
        start = m.end() - 1
        out.append((m.group(1), m.group(2), start, match_brace(src, start)))
    return out


def fields_of(body):
    """Field declarations: lines at class level before the first method."""
    head = body.split('public', 1)[0]
    return re.findall(r'^\s*([\w\[\]]+)\s+(\w+)\s*;', head, re.M)


def for_to_while(s):
    while True:
        m = re.search(r'for\s*\(\s*(\w+)\s*=\s*([^;]+);\s*([^;]+);\s*(\w+)\s*=\s*([^)]+)\)\s*', s)
        if not m:
            return s
        init = f"{m.group(1)} = {m.group(2).strip()};"
        cond, step = m.group(3).strip(), f"{m.group(4)} = {m.group(5).strip()};"
        rest = s[m.end():]
        if rest.startswith('{'):
            end = match_brace(rest, 0)
            body = rest[1:end]
            tail = rest[end + 1:]
        else:
            end = rest.index(';') + 1
            body = rest[:end]
            tail = rest[end:]
        s = s[:m.start()] + f"{init}\nwhile ({cond}) {{{body}\n{step}\n}}" + tail


def convert(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r'\bfinal\s+', '', src)

    cls = classes(src)
    main_name = cls[0][0]
    field_types = {}                         # field -> type
    owners = {}                              # class -> [(type, field)]
    returns = {}                             # method -> return type
    for name, _, a, b in cls:
        body = src[a + 1:b]
        owners[name] = fields_of(body) if name != main_name else []
        for t, f in owners[name]:
            field_types[f] = t
        for t, m in re.findall(r'public\s+([\w\[\]]+)\s+(\w+)\s*\(', body):
            returns[m] = t

    # Move main's body into <Main>Body.run().
    _, _, a, b = cls[0]
    main_cls = src[a + 1:b]
    mm = re.search(r'public\s+static\s+void\s+main\s*\(\s*String\s*\[\s*\]\s*\w+\s*\)\s*\{', main_cls)
    mstart = mm.end() - 1
    mbody = main_cls[mstart + 1:match_brace(main_cls, mstart)]
    pieces = [f"class {main_name} {{\n    public static void main(String[] a) {{\n"
              f"        System.out.println(new {main_name}Body().run());\n    }}\n}}\n",
              f"class {main_name}Body {{\n    public int run() {{\n{mbody}\n        return 0;\n    }}\n}}\n"]
    for name, parent, a, b in cls[1:]:
        ext = f" extends {parent}" if parent else ""
        acc = "".join(f"    public {t} get_{f}() {{ return {f}; }}\n"
                      f"    public int set_{f}({t} nv) {{ {f} = nv; return 0; }}\n" for t, f in owners[name])
        pieces.append(f"class {name}{ext} {{{src[a + 1:b]}\n{acc}}}\n")
        for t, f in owners[name]:
            returns["get_" + f] = t
            returns["set_" + f] = "int"
    out = "\n".join(pieces)

    out = for_to_while(out)
    # Field writes and reads through an object.
    out = re.sub(r'^(\s*)(\w+)\.(\w+)\s*=\s*([^;]+);', lambda m: f"{m.group(1)}DUMMY:int = {m.group(2)}.set_{m.group(3)}({m.group(4).strip()});", out, flags=re.M)
    out = re.sub(r'=\s*(\w+)\.(\w+)\s*;', lambda m: f"= {m.group(1)}.get_{m.group(2)}();", out)
    out = re.sub(r'\bthis\.get_(\w+)\(\)', r'\1', out)
    # Call statements.
    def call_stmt(m):
        t = returns.get(m.group(3), "int")
        return f"{m.group(1)}DUMMY:{t} = {m.group(2)}.{m.group(3)}({m.group(4)});"
    out = re.sub(r'^(\s*)(\w+)\.(\w+)\(([^;]*)\);', call_stmt, out, flags=re.M)
    # Operators and literals.
    out = re.sub(r'([=(,]\s*)-\s*(\d+)', r'\1(0 - \2)', out)
    out = re.sub(r'([=(,]\s*)\+\s*(\d+)', r'\1\2', out)
    out = re.sub(r'(\w+)\s*<\s*(\w+)', r'\1 <= (\2 - 1)', out)
    out = re.sub(r'(?<![&|])([&|])(?![&|])', r'\1\1', out)

    # Declare the dummy locals each method needs.
    def declare(m):
        body = m.group(0)
        kinds = sorted(set(re.findall(r'DUMMY:([\w\[\]]+)', body)))
        if not kinds:
            return body
        names = {k: "dummy_" + re.sub(r'\W', '_', k) for k in kinds}
        body = re.sub(r'DUMMY:([\w\[\]]+)', lambda d: names[d.group(1)], body)
        decls = "".join(f"\n        {k} {names[k]};" for k in kinds)
        return re.sub(r'(public[^{]*\{)', lambda h: h.group(1) + decls, body, count=1)
    out = re.sub(r'public\s+[\w\[\]]+\s+\w+\s*\([^)]*\)\s*\{(?:[^{}]|\{(?:[^{}]|\{(?:[^{}]|\{[^{}]*\})*\})*\})*\}', declare, out)
    return out


if __name__ == "__main__":
    sys.stdout.write(convert(open(sys.argv[1]).read()))
