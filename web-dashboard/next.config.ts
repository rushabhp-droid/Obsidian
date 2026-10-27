import type { NextConfig } from "next";

// Localhost fallback for the named tunnel: these paths belong to Spring, everything
// else to Next.js. Keep the list identical to the cloudflared ingress (CLAUDE.md,
// "Routing / origin"). beforeFiles so /login/webauthn and /login/oauth2 never reach
// the Next.js /login page.
const SPRING_PATHS = ["/api", "/ws", "/webauthn", "/login/webauthn", "/oauth2", "/login/oauth2"];
const springOrigin = process.env.SPRING_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return {
      beforeFiles: SPRING_PATHS.map((path) => ({
        source: `${path}/:path*`,
        destination: `${springOrigin}${path}/:path*`,
      })),
      afterFiles: [],
      fallback: [],
    };
  },
};

export default nextConfig;
