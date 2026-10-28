/* Copy the shared web UI (MatinAIWeb) into app-ui/ before packaging. */
const fs = require("fs");
const path = require("path");

const src = path.join(__dirname, "..", "MatinAIWeb");
const dest = path.join(__dirname, "app-ui");

if (!fs.existsSync(src)) {
  console.error("MatinAIWeb not found at " + src + " — run from inside the repo.");
  process.exit(1);
}

fs.rmSync(dest, { recursive: true, force: true });
fs.cpSync(src, dest, { recursive: true });
// developer docs are not needed inside the packaged app
fs.rmSync(path.join(dest, "proxy"), { recursive: true, force: true });
console.log("app-ui <- MatinAIWeb copied OK");
