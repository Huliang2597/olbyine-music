import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

export default defineConfig({
  base: "./",
  build: {
    outDir: fileURLToPath(new URL("../app/src/native/assets/amll/", import.meta.url)),
    emptyOutDir: true,
    assetsInlineLimit: 0,
    target: "chrome91",
  },
});
