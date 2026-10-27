import { AppSidebar } from "@/components/shell/app-sidebar";
import { ContextHeader } from "@/components/shell/context-header";
import { LiveProvider } from "@/components/shell/live-provider";

// Sidebar + auth guard (TODO.md §3). middleware.ts redirects requests without a
// session cookie; LiveProvider sends a 401 from /api/v1/auth/me back to /login.
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <LiveProvider>
      <div className="flex min-h-screen">
        <AppSidebar />
        <div className="flex min-w-0 flex-1 flex-col">
          <ContextHeader />
          <main className="flex flex-col gap-6 p-8">{children}</main>
        </div>
      </div>
    </LiveProvider>
  );
}
