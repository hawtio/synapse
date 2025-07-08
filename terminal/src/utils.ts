
export function isError(obj: unknown): obj is Error {
  return obj instanceof Error
}
