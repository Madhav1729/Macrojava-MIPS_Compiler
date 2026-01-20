#!/usr/bin/env bash
# Usage: tests/run_tests.sh [--update]

set -u
root=$(cd "$(dirname "$0")/.." && pwd)
mjc=${MJC:-$root/build/bin/mjc}

update=0
case "${1:-}" in
    "") ;;
    --update) update=1 ;;
    *) echo "usage: $0 [--update]" >&2; exit 2 ;;
esac

if [ ! -x "$mjc" ]; then
    echo "error: $mjc not found; build it with 'make'" >&2
    exit 2
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

if [ -t 1 ]; then green=$'\033[32m' red=$'\033[31m' reset=$'\033[0m'; else green='' red='' reset=''; fi
passed=0
failed=0
pass() { echo "  ${green}PASS${reset}  $1"; passed=$((passed + 1)); }
fail() { echo "  ${red}FAIL${reset}  $1: $2"; failed=$((failed + 1)); }

have_spim=1
if [ -z "${MJC_SPIM:-}" ] && ! command -v spim >/dev/null 2>&1 && ! command -v qtspim >/dev/null 2>&1; then
    have_spim=0
    echo "note: SPIM not found, so program output is not checked (only the generated assembly)"
fi

echo "Programs (testcases/):"
for src in "$root"/testcases/*.java; do
    name=$(basename "$src" .java)
    expected=$root/outputs/$name
    asm=$tmp/$name.s
    out=$tmp/$name.out

    if [ "$have_spim" = 1 ]; then
        "$mjc" "$src" -o "$asm" --run >"$out" 2>"$tmp/$name.err"
    else
        "$mjc" "$src" -o "$asm" 2>"$tmp/$name.err"
    fi
    status=$?
    if [ ! -f "$asm" ]; then
        fail "$name" "compilation failed: $(head -n 1 "$tmp/$name.err")"
        continue
    fi
    if [ "$status" -ne 0 ]; then
        fail "$name" "SPIM exited with status $status"
        continue
    fi

    if [ "$update" = 1 ]; then
        mkdir -p "$expected"
        cp "$asm" "$expected/$name.s"
        [ "$have_spim" = 1 ] && cp "$out" "$expected/output.txt"
        pass "$name (expected results updated)"
        continue
    fi

    if ! cmp -s "$asm" "$expected/$name.s"; then
        fail "$name" "assembly differs from outputs/$name/$name.s"
    elif [ "$have_spim" = 1 ] && ! cmp -s "$out" "$expected/output.txt"; then
        fail "$name" "program output differs from outputs/$name/output.txt"
        diff "$expected/output.txt" "$out" | head -n 10 | sed 's/^/        /'
    else
        pass "$name"
    fi
done

echo "Error diagnostics (tests/errors/):"
for src in "$root"/tests/errors/*.java; do
    name=$(basename "$src" .java)
    want=$(sed -n '1s|^// *expect: *||p' "$src")
    "$mjc" "$src" -o "$tmp/$name.s" 2>"$tmp/$name.err"
    status=$?
    if [ -z "$want" ]; then
        fail "$name" "first line must be '// expect: <part of the error message>'"
    elif [ "$status" -ne 1 ]; then
        fail "$name" "expected exit status 1, got $status"
    elif ! grep -qF -- "$want" "$tmp/$name.err"; then
        fail "$name" "expected an error containing \"$want\", got: $(head -n 1 "$tmp/$name.err")"
    else
        pass "$name"
    fi
done

echo
echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
