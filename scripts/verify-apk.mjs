#!/usr/bin/env node
/**
 * Post-build APK verifier.
 * - Confirms the APK exists, is signed, and is non-trivial in size.
 * - Computes SHA-256 of the APK file.
 * - Writes BUILD_INFO.json next to the APK with all signals.
 * - Fails (exit 1) if anything is wrong.
 */
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const outDir = join(root, "android", "app", "build", "outputs", "apk", "release");

function fail(msg) {
  console.error(`[verify-apk] FAIL: ${msg}`);
  process.exit(1);
}

if (!existsSync(outDir)) fail(`missing release output dir: ${outDir}`);

const apks = readdirSync(outDir).filter((f) => f.endsWith(".apk"));
if (apks.length === 0) fail("no APK found in release output");

const apkName = apks[0];
const apkPath = join(outDir, apkName);
const apkBuf = readFileSync(apkPath);
const size = apkBuf.length;
if (size < 1_000_000) fail(`APK suspiciously small: ${size} bytes`);

const sha256 = createHash("sha256").update(apkBuf).digest("hex");

// Read keystore size + locate it
const ksPath = process.env.AETHERX_KEYSTORE_PATH || join(root, "android/app/release.keystore");
const keystoreOk = existsSync(ksPath) && statSync(ksPath).size > 0;
if (!keystoreOk) fail(`keystore missing or empty: ${ksPath}`);

// Confirm APK has v2/v3 signature block (look for APK Signing Block magic).
const SIG_MAGIC = Buffer.from("APK Sig Block 42");
const signed = apkBuf.includes(SIG_MAGIC);
if (!signed) fail("APK has no v2/v3 signing block (would trigger Play Protect)");

const buildVersion = process.env.AETHERX_BUILD_VERSION || "unknown";
const buildTimestamp = process.env.AETHERX_BUILD_TIMESTAMP || new Date().toISOString();
const buildId = process.env.AETHERX_BUILD_ID || createHash("sha256")
  .update(`${buildVersion}|${buildTimestamp}`)
  .digest("hex")
  .slice(0, 12);

const info = {
  apk: apkName,
  apkSizeBytes: size,
  apkSha256: sha256,
  signed: true,
  keystorePath: ksPath,
  buildVersion,
  buildTimestamp,
  buildId,
};

writeFileSync(join(outDir, "BUILD_INFO.json"), JSON.stringify(info, null, 2));
console.log("[verify-apk] OK");
console.log(JSON.stringify(info, null, 2));
