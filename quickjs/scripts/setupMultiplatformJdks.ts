import { $, ShellOutput } from "bun";
import * as path from "path";
import * as fs from "fs/promises";

type Jdk = {
  version: string;
  name: string;
  homePath: string;
  homeEnvVar: string;
  url: string;
  sha256: string;
};

const USER_HOME = process.env.HOME!;
const JDK_ROOT = path.join(USER_HOME, "jdks");

const JDK_LIST: Jdk[] = [
  {
    version: "21",
    name: "linux_aarch64",
    homePath: "jdk-21.0.2+13",
    homeEnvVar: "java_home_linux_aarch64",
    url: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.2_13.tar.gz",
    sha256: "3ce6a2b357e2ef45fd6b53d6587aa05bfec7771e7fb982f2c964f6b771b7526a",
  },
  {
    version: "21",
    name: "linux_x64",
    homePath: "jdk-21.0.2+13",
    homeEnvVar: "java_home_linux_x64",
    url: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_x64_linux_hotspot_21.0.2_13.tar.gz",
    sha256: "454bebb2c9fe48d981341461ffb6bf1017c7b7c6e15c6b0c29b959194ba3aaa5",
  },
  {
    version: "21",
    name: "macos_aarch64",
    homePath: "jdk-21.0.2+13/Contents/Home",
    homeEnvVar: "java_home_macos_aarch64",
    url: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.2_13.tar.gz",
    sha256: "57d9e0f0e8639f9f2fb1837518fd83043f23953ff69a677f885aa060994d0c19",
  },
  {
    version: "21",
    name: "macos_x64",
    homePath: "jdk-21.0.2+13/Contents/Home",
    homeEnvVar: "java_home_macos_x64",
    url: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_x64_mac_hotspot_21.0.2_13.tar.gz",
    sha256: "ba696ec46c1ca2b1b64c4e9838e21a2d62a1a4b6857a0770adc64451510065db",
  },
  {
    version: "21",
    name: "windows_x64",
    homePath: "jdk-21.0.2+13",
    homeEnvVar: "java_home_windows_x64",
    url: "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.2%2B13/OpenJDK21U-jdk_x64_windows_hotspot_21.0.2_13.zip",
    sha256: "8780b07ae0a9836285a86a5b6d2b8a0b82acf97258622d44c619d59998a1da7b",
  },
];

async function isFileExists(filepath: string) {
  try {
    await fs.access(filepath);
    return true;
  } catch (err) {
    return false;
  }
}

async function fileSha256(filepath: string) {
  const out = await $`shasum -a 256 ${filepath}`.text();
  return out.split(" ")[0];
}

async function throwIfStderrNotEmpty(output: ShellOutput) {
  const err = output.stderr.toString().trim();
  if (err.length > 0) {
    throw new Error(err);
  }
  return output;
}

async function downloadAndExtractJdk(jdk: Jdk) {
  const filename = path.basename(jdk.url);
  const filepath = path.join(JDK_ROOT, filename);
  const jdkFullName = `JDK ${jdk.version} for '${jdk.name}'`;

  console.log(`Downloading ${jdkFullName}...`);
  let downloaded = false;
  if (await isFileExists(filepath)) {
    const sha256 = await fileSha256(filepath);
    console.log(`Sha256 of the downloaded file: '${sha256}'`);
    if (sha256 === jdk.sha256) {
      console.log(`${jdkFullName} is downloaded. SKIP`);
      downloaded = true;
    } else {
      console.log(`Removing broken ${jdkFullName}...`);
      throwIfStderrNotEmpty(await $`rm ${filepath}`);
    }
  }
  if (!downloaded) {
    throwIfStderrNotEmpty(await $`wget --quiet -P ${JDK_ROOT} ${jdk.url}`);
  }

  console.log(`Extracting ${jdkFullName}...`);
  const extractPath = path.join(JDK_ROOT, jdk.name);

  await $`mkdir ${extractPath}`;
  if (filename.endsWith(".gz")) {
    throwIfStderrNotEmpty(await $`tar -xzf ${filepath} -C ${extractPath}`);
  } else if (filename.endsWith(".zip")) {
    throwIfStderrNotEmpty(await $`unzip -o --q ${filepath} -d ${extractPath}`);
  } else {
    throw new Error(`Unknown JDK archive: ${filename}`);
  }

  return [jdk.homeEnvVar, path.join(extractPath, jdk.homePath)] as const;
}

async function createEnvVars(vars: (readonly [string, string])[]) {
  const bashrcFilepath = path.join(USER_HOME, ".java-home-vars");
  let bashrc = "# Generated by the JDK setup script.";
  for (const envVar of vars) {
    const varName = envVar[0];
    const varValue = envVar[1];
    bashrc += `\nexport ${varName}=${varValue}`;
  }
  await fs.writeFile(bashrcFilepath, bashrc);

  console.log();
  console.log(
    "Updated environment variables, please run the following command to take effect:"
  );
  console.log();
  console.log(`>>> source ~/${path.basename(bashrcFilepath)}`);
  console.log();
}

const start = Date.now();

console.log(`Installing ${JDK_LIST.length} JDKs...`);

console.log();
console.log("JDK ROOT:", JDK_ROOT);
console.log();

await $`mkdir ${JDK_ROOT}`;

// Download and extract
const envVars = await Promise.all(
  JDK_LIST.map((jdk) => downloadAndExtractJdk(jdk))
);

// Create env vars
console.log();
console.log("Creating environment variables...");
await createEnvVars(envVars);

const end = Date.now();

const elapsed = ((end - start) / 1000).toFixed(2);

console.log();
console.log(
  `All ${JDK_LIST.length} JDKs are installed. Time elapsed: ${elapsed}s.`
);
