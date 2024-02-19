import { $, ShellOutput } from "bun";
import * as fs from "fs/promises";
import * as os from "os";
import * as path from "path";

type BenchmarkResult = {
  name: string;
  iterations: number;
  scoreUnit: string;
  score: number;
};

async function throwIfGradleTaskNotSuccuss(output: ShellOutput) {
  const stdout = output.stdout.toString().trim();
  if (!stdout.includes("BUILD SUCCESSFUL")) {
    throw new Error("Gradle task failed.");
  }
  return output;
}

const osName = os.platform();
const osArch = os.arch();

if (osName !== "linux" && osName !== "win32") {
  throw new Error(`Unsupported OS: ${osName}`);
}
if (osArch !== "x64") {
  throw new Error(`Unsupported architecture: ${osArch}`);
}

// Run benchmarks

console.log(`Running benchmarks on ${osName} ${osArch}...`);

await throwIfGradleTaskNotSuccuss(await $`./gradlew :benchmark:jvmBenchmark`);
if (osName === "linux") {
  await throwIfGradleTaskNotSuccuss(
    await $`./gradlew :benchmark:linuxX64Benchmark`
  );
} else {
  await throwIfGradleTaskNotSuccuss(
    await $`./gradlew :benchmark:mingwX64Benchmark`
  );
}

// Collect results

console.log("Collecting results...");

const resultDir = "./benchmark/build/reports/benchmarks/main";
const dirs = (await fs.readdir(resultDir)).sort((a, b) => b.localeCompare(a));

const jvmResultFilename = "jvm.json";
let jvmResult: any | null = null;

const nativeResultFilename =
  osName === "linux" ? "linuxX64.json" : "mingwX64.json";
let nativeResult: any | null = null;

for (const dir of dirs) {
  const dirPath = path.join(resultDir, dir);
  if (!(await fs.stat(dirPath)).isDirectory()) {
    continue;
  }

  const jvmResultPath = path.join(dirPath, jvmResultFilename);
  try {
    const jvmResultContent = await fs.readFile(jvmResultPath, "utf-8");
    jvmResult = JSON.parse(jvmResultContent);
  } catch (e) {}

  const nativeResultPath = path.join(dirPath, nativeResultFilename);
  try {
    const nativeResultContent = await fs.readFile(nativeResultPath, "utf-8");
    nativeResult = JSON.parse(nativeResultContent);
  } catch (e) {}

  if (jvmResult != null && nativeResult != null) {
    break;
  }
}

if (jvmResult == null) {
  throw new Error("No JVM benchmark result found.");
}
if (nativeResult == null) {
  throw new Error("No native benchmark result found.");
}

// Write results to README.md

console.log("Writing results to benchmark/README.md...");

function benchmarkResultAsTableLines(result: any): string {
  const items: BenchmarkResult[] = [];
  for (const item of result) {
    items.push({
      name: item.benchmark.split(".").pop(),
      iterations: item.measurementIterations,
      scoreUnit: item.primaryMetric.scoreUnit,
      score: item.primaryMetric.score.toFixed(2),
    });
  }
  return items
    .map((item) => {
      return `| ${item.name} | ${item.iterations} | ${item.score} | ${item.scoreUnit} |`;
    })
    .join("\n");
}

const jvmTableLines = benchmarkResultAsTableLines(jvmResult);
const nativeTableLines = benchmarkResultAsTableLines(nativeResult);

const date = new Date().toLocaleString("en-US");

const cpusMap = new Map<string, number>();
for (const cpu of os.cpus()) {
  cpusMap.set(cpu.model, (cpusMap.get(cpu.model) ?? 0) + 1);
}
const cpus = [...cpusMap.entries()]
  .map(([cpu, count]) => `${cpu} x ${count}`)
  .join("\n");

const totalMemoryGB = (os.totalmem() / (1024 * 1024 * 1024)).toFixed(1);

const README = `# Benchmark Results

Generated on ${date}

### Test environment

System: ${os.platform()} ${os.arch()}

CPUs: ${cpus}

Memory: ${totalMemoryGB} GB

### JVM Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
${jvmTableLines}

### Kotlin/Native Results

| Name | Iterations | Score | Unit |
| --- | --- | --- | --- |
${nativeTableLines}

### Notes

The engine creation times are included in define[Xzy]Bindings benchmarks, so the actual results should be much faster, but the relative results should remain the same.
`;

await fs.writeFile("./benchmark/README.md", README);

console.log("Done.");
