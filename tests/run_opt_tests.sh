#!/usr/bin/env bash
# Usage: tests/run_opt_tests.sh [program.java ...]
#        (default: testcases/*.java and tests/opt/**/*.java)
#
# Each program must print the same at -O0, -O1 and -O2 as Java does (or as -O0
# if javac rejects it), and the -O1 program must itself run under Java.
# Directives: // mjc-test: compile-only | contains-O1 <text> | absent-O2 <text>

set -u
root=$(cd "$(dirname "$0")/.." && pwd)
mjc=${MJC:-$root/build/bin/mjc}
[ -x "$mjc" ] || { echo "error: $mjc not found; build it with 'make'" >&2; exit 2; }
command -v java >/dev/null || { echo "error: java is needed for the reference outputs" >&2; exit 2; }
command -v "${MJC_SPIM:-spim}" >/dev/null || { echo "error: SPIM is needed to run programs" >&2; exit 2; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
if [ -t 1 ]; then green=$'\033[32m' red=$'\033[31m' reset=$'\033[0m'; else green='' red='' reset=''; fi
passed=0
failed=0
fail() { echo "  ${red}FAIL${reset}  $1: $2"; failed=$((failed + 1)); }

if [ $# -eq 0 ]; then
    set -- "$root"/testcases/*.java $(find "$root/tests/opt" -name '*.java' | sort)
fi

squash() { tr -d ' \t\r\n'; }

# Run a Java source file in its own directory (single-file source launcher).
run_java() {
    local dir=$tmp/java$2
    rm -rf "$dir"; mkdir -p "$dir"
    cp "$1" "$dir/Main.java"
    (cd "$dir" && timeout 60 java -Xss64m Main.java)
}

for src in "$@"; do
    name=${src#"$root"/}
    d=$tmp/case
    rm -rf "$d"; mkdir -p "$d"
    directives=$(sed -n 's|^// *mjc-test: *||p' "$src")
    run=1
    grep -qx 'compile-only' <<<"$directives" && run=0

    if ! "$mjc" "$src" --emit=minijava -o "$d/expanded.java" 2>"$d/err"; then
        fail "$name" "does not compile at -O0: $(head -n 1 "$d/err")"
        continue
    fi

    ok=1
    reference=""
    have_ref=0
    if [ $run = 1 ] && run_java "$d/expanded.java" ref >"$d/java.out" 2>"$d/java.err"; then
        reference=$d/java.out
        have_ref=1
    fi

    for o in 0 1 2; do
        if ! "$mjc" "$src" -O$o -Wno-uninitialized -o "$d/O$o.s" 2>"$d/err"; then
            fail "$name" "-O$o compilation failed: $(head -n 1 "$d/err")"; ok=0; break
        fi
        [ $run = 1 ] || continue
        if ! timeout 120 "$mjc" "$src" -O$o -Wno-uninitialized --run -o "$d/O$o.s" >"$d/O$o.out" 2>"$d/err"; then
            fail "$name" "-O$o program failed in SPIM: $(head -n 1 "$d/err")"; ok=0; break
        fi
        if [ $have_ref = 0 ]; then reference=$d/O0.out; have_ref=1; fi
        if ! cmp -s "$reference" "$d/O$o.out"; then
            fail "$name" "-O$o output differs from the reference"
            diff "$reference" "$d/O$o.out" | head -n 6 | sed 's/^/        /'
            ok=0; break
        fi
    done
    [ $ok = 1 ] || continue

    for o in 1 2; do "$mjc" "$src" -O$o -Wno-uninitialized --emit=opt -o "$d/O$o.opt" 2>/dev/null; done

    # The -O1 program is MiniJava, hence Java: it must compile and agree too.
    if [ $run = 1 ] && [ -s "$d/java.out" ]; then
        if ! run_java "$d/O1.opt" opt >"$d/opt.out" 2>"$d/opt.err"; then
            fail "$name" "the -O1 program is not valid Java: $(grep -m1 error: "$d/opt.err")"; continue
        elif ! cmp -s "$d/java.out" "$d/opt.out"; then
            fail "$name" "the -O1 program prints something else under Java"; continue
        fi
    fi

    while read -r kind text; do
        [ -n "$kind" ] || continue
        case $kind in
            compile-only) ;;
            contains-O1|contains-O2|absent-O1|absent-O2)
                level=${kind#*-}
                want=$(printf '%s' "$text" | squash)
                if squash <"$d/$level.opt" | grep -qF -- "$want"; then found=1; else found=0; fi
                if [ "${kind%%-*}" = contains ] && [ $found = 0 ]; then
                    fail "$name" "-$level program does not contain '$text'"; ok=0
                elif [ "${kind%%-*}" = absent ] && [ $found = 1 ]; then
                    fail "$name" "-$level program still contains '$text'"; ok=0
                fi ;;
            *) fail "$name" "unknown directive '$kind'"; ok=0 ;;
        esac
    done <<<"$directives"
    [ $ok = 1 ] || continue

    note=""
    [ $run = 0 ] && note=" (compile only)"
    [ $run = 1 ] && [ ! -s "$d/java.out" ] && note=" (not valid Java: checked against -O0)"
    echo "  ${green}PASS${reset}  $name$note"
    passed=$((passed + 1))
done

echo
echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
