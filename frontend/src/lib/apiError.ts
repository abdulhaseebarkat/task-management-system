import { isAxiosError } from "axios"

/** Surfaces the backend's user-friendly message (and any field errors) instead of a generic failure text. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError(error)) {
    const data = error.response?.data as { message?: string; fieldErrors?: string[] | null } | undefined
    if (data?.fieldErrors?.length) return `${data.message ?? fallback} ${data.fieldErrors.join("; ")}`
    if (data?.message) return data.message
  }
  return fallback
}
