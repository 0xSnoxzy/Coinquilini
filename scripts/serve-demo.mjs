import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
const root = path.resolve(
  fileURLToPath(
    new URL("../frontend/dist/coinquilini/browser/", import.meta.url),
  ),
);
const port = Number(process.env.FRONTEND_PORT || 3000);
try {
  await stat(path.join(root, "index.html"));
} catch {
  console.error("Compila prima: cd frontend, npm ci, npm run build.");
  process.exit(1);
}
const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".woff2": "font/woff2",
  ".png": "image/png",
};
const routes = new Set([
  "/",
  "/per-me",
  "/spese",
  "/lista",
  "/mansioni",
  "/impostazioni",
  "/admin",
]);
createServer(async (req, res) => {
  try {
    if (!["GET", "HEAD"].includes(req.method)) {
      res.writeHead(405);
      res.end();
      return;
    }
    const pathname = decodeURIComponent(
      new URL(req.url, "http://localhost").pathname,
    );
    if (
      pathname.includes("\0") ||
      pathname.split("/").some((p) => p.startsWith("."))
    ) {
      res.writeHead(400);
      res.end();
      return;
    }
    const file = routes.has(pathname.replace(/\/$/, "") || "/")
      ? path.join(root, "index.html")
      : path.resolve(root, "." + pathname);
    if (!file.startsWith(root + path.sep)) {
      res.writeHead(403);
      res.end();
      return;
    }
    const bytes = await readFile(file);
    res.setHeader(
      "Content-Type",
      types[path.extname(file)] || "application/octet-stream",
    );
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.end(req.method === "HEAD" ? undefined : bytes);
  } catch (e) {
    res.writeHead(e.code === "ENOENT" ? 404 : 400);
    res.end("Risorsa non disponibile.");
  }
}).listen(port, "127.0.0.1", () =>
  console.log(`Anteprima Coinquilini: http://localhost:${port}`),
);
