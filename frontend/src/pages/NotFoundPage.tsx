export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-2 bg-background">
      <div className="text-lg font-semibold text-foreground">Page not found</div>
      <a href="/" className="text-sm text-primary">
        Go back
      </a>
    </div>
  )
}
