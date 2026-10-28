/** A failed API call; `status` is the HTTP status (shared by live and sample-data modes). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}
