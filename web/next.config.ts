import type { NextConfig } from "next";

const backend = process.env.BACKEND_URL || "http://127.0.0.1:8081";
const config: NextConfig = {
  async rewrites() {
    return [{ source: "/api/v1/:path*", destination: `${backend}/api/v1/:path*` }];
  },
  async headers() {
    return [{ source: "/:path*", headers: [
      { key: "Referrer-Policy", value: "no-referrer" },
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "Cache-Control", value: "no-store" },
    ] }];
  },
};
export default config;
