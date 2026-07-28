package com.dokar.quickjs.benchmark

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Suppress("unused")
@State(Scope.Benchmark)
class EvaluateBenchmark {
    private lateinit var quickJs: QuickJs
    private lateinit var fibonacciBytecode: ByteArray
    private lateinit var loopsBytecode: ByteArray
    private lateinit var functionCallsBytecode: ByteArray
    private lateinit var propertyAccessBytecode: ByteArray
    private lateinit var arrayOperationsBytecode: ByteArray
    private lateinit var typedArrayBytecode: ByteArray
    private lateinit var jsonBytecode: ByteArray
    private lateinit var regexpBytecode: ByteArray
    private lateinit var mapSetBytecode: ByteArray
    private lateinit var allocationBytecode: ByteArray
    private lateinit var bigIntBytecode: ByteArray

    @Setup
    fun setup() {
        quickJs = QuickJs.create(Dispatchers.Default)
        quickJs.define("benchmarkBindings") {
            asyncFunction("identity") { args -> args.firstOrNull() }
        }
        fibonacciBytecode = quickJs.compile(FIBONACCI)
        loopsBytecode = quickJs.compile(LOOPS)
        functionCallsBytecode = quickJs.compile(FUNCTION_CALLS)
        propertyAccessBytecode = quickJs.compile(PROPERTY_ACCESS)
        arrayOperationsBytecode = quickJs.compile(ARRAY_OPERATIONS)
        typedArrayBytecode = quickJs.compile(TYPED_ARRAY)
        jsonBytecode = quickJs.compile(JSON)
        regexpBytecode = quickJs.compile(REGEXP)
        mapSetBytecode = quickJs.compile(MAP_SET)
        allocationBytecode = quickJs.compile(ALLOCATION)
        bigIntBytecode = quickJs.compile(BIG_INT)
    }

    @TearDown
    fun cleanup() {
        quickJs.close()
    }

    @Benchmark
    fun evaluateTrivialSource() = evaluate("40 + 2")

    @Benchmark
    fun evaluateLargeSource() = evaluate(LARGE_SOURCE)

    @Benchmark
    fun executeFibonacciBytecode() = evaluate(fibonacciBytecode)

    @Benchmark
    fun executeLoopsBytecode() = evaluate(loopsBytecode)

    @Benchmark
    fun executeFunctionCallsBytecode() = evaluate(functionCallsBytecode)

    @Benchmark
    fun executePropertyAccessBytecode() = evaluate(propertyAccessBytecode)

    @Benchmark
    fun executeArrayOperationsBytecode() = evaluate(arrayOperationsBytecode)

    @Benchmark
    fun executeTypedArrayBytecode() = evaluate(typedArrayBytecode)

    @Benchmark
    fun executeJsonBytecode() = evaluate(jsonBytecode)

    @Benchmark
    fun executeRegExpBytecode() = evaluate(regexpBytecode)

    @Benchmark
    fun executeMapSetBytecode() = evaluate(mapSetBytecode)

    @Benchmark
    fun executeAllocationBytecode() = evaluate(allocationBytecode)

    @Benchmark
    fun evaluatePromisesSource() = evaluate(PROMISES)

    @Benchmark
    fun evaluatePromisesWithBindingsSource() = evaluate(PROMISES_WITH_BINDINGS)

    @Benchmark
    fun executeBigIntBytecode() = evaluate(bigIntBytecode)

    private fun evaluate(code: String) = runBlocking {
        quickJs.evaluate<Long>(code)
    }

    private fun evaluate(bytecode: ByteArray) = runBlocking {
        quickJs.evaluate<Long>(bytecode)
    }

    private companion object {
        val LARGE_SOURCE = buildString {
            append("(() => {")
            repeat(500) { index ->
                append("function f$index(value) { return value + $index; }")
            }
            append("return f499(1); })()")
        }

        const val FIBONACCI = """
            (() => {
                function fib(n) {
                    return n < 2 ? n : fib(n - 1) + fib(n - 2);
                }
                return fib(20);
            })()
        """

        const val LOOPS = """
            (() => {
                let sum = 0;
                for (let i = 0; i < 100000; i++) {
                    sum += i % 10;
                }
                return sum;
            })()
        """

        const val FUNCTION_CALLS = """
            (() => {
                function add(a, b) {
                    return a + b;
                }
                let value = 0;
                for (let i = 0; i < 50000; i++) {
                    value = add(value, i & 7);
                }
                return value;
            })()
        """

        const val PROPERTY_ACCESS = """
            (() => {
                const object = { a: 1, b: 2, c: 3, d: 4 };
                let sum = 0;
                for (let i = 0; i < 50000; i++) {
                    object.a = i;
                    object.b = i + 1;
                    sum += object.a + object.b + object.c + object.d;
                }
                return sum;
            })()
        """

        const val ARRAY_OPERATIONS = """
            (() => {
                const array = [];
                for (let i = 0; i < 20000; i++) {
                    array.push(i);
                }
                let sum = 0;
                for (let i = 0; i < array.length; i++) {
                    array[i] += 1;
                    sum += array[i];
                }
                while (array.length > 10000) {
                    sum += array.pop();
                }
                return sum;
            })()
        """

        const val TYPED_ARRAY = """
            (() => {
                const array = new Int32Array(20000);
                for (let i = 0; i < array.length; i++) {
                    array[i] = i;
                }
                let sum = 0;
                for (let i = 0; i < array.length; i++) {
                    sum += array[i];
                }
                return sum;
            })()
        """

        const val JSON = """
            (() => {
                const source = JSON.stringify(
                    Array.from({ length: 1000 }, (_, i) => ({
                        id: i,
                        name: "item-" + i,
                        active: (i & 1) === 0
                    }))
                );
                const parsed = JSON.parse(source);
                return JSON.stringify(parsed).length;
            })()
        """

        const val REGEXP = """
            (() => {
                const input = "quickjs-123 ".repeat(2000);
                const matches = input.match(/[a-z]+-\d+/g);
                return input.replace(/(\w+)-(\d+)/g, "${'$'}2:${'$'}1").length + matches.length;
            })()
        """

        const val MAP_SET = """
            (() => {
                const map = new Map();
                const set = new Set();
                for (let i = 0; i < 10000; i++) {
                    map.set(i, i * 2);
                    set.add(i);
                }
                let sum = 0;
                for (let i = 0; i < 10000; i++) {
                    if (set.has(i)) {
                        sum += map.get(i);
                    }
                }
                return sum;
            })()
        """

        const val ALLOCATION = """
            (() => {
                let objects = [];
                let sum = 0;
                for (let round = 0; round < 10; round++) {
                    objects = [];
                    for (let i = 0; i < 5000; i++) {
                        objects.push({
                            index: i,
                            values: [i, i + 1, i + 2],
                            label: "item-" + i
                        });
                    }
                    sum += objects[objects.length - 1].index;
                }
                return sum;
            })()
        """

        const val PROMISES = """
            var promiseSum = 0;
            for (let i = 0; i < 100; i++) {
                promiseSum += await Promise.resolve(i);
            }
            promiseSum;
        """

        const val PROMISES_WITH_BINDINGS = """
            var bindingPromiseSum = 0;
            for (let i = 0; i < 100; i++) {
                bindingPromiseSum += await benchmarkBindings.identity(i);
            }
            bindingPromiseSum;
        """

        const val BIG_INT = """
            (() => {
                let value = 1n;
                for (let i = 1n; i <= 2000n; i++) {
                    value = (value * i + i) % 1000000007n;
                }
                return Number(value);
            })()
        """
    }
}
