import { NextResponse, type NextRequest } from "next/server";

// UX guard only: Spring enforces auth on every /api call. Skipped in sample-data
// mode, where there is no session to check.
export function middleware(request: NextRequest) {
  if (process.env.NEXT_PUBLIC_API_MODE !== "live" || request.cookies.has("__Host-session")) {
    return NextResponse.next();
  }
  const login = request.nextUrl.clone();
  login.pathname = "/login";
  login.search = "";
  return NextResponse.redirect(login);
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/threats/:path*",
    "/emails/:path*",
    "/agents/:path*",
    "/code/:path*",
    "/firewall/:path*",
    "/consumer-reports/:path*",
    "/messaging/:path*",
    "/reports/:path*",
    "/audit/:path*",
    "/settings/:path*",
  ],
};
