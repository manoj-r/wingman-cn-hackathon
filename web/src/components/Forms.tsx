"use client";
import type { ComponentProps } from "react";
import { ApiError } from "../lib/api";

type FieldProps = { label: string; name: string; error?: string; multiline?: boolean } & ComponentProps<"input">;
export function Field({ label, name, error, multiline, ...props }: FieldProps) {
  const common = { id: name, name, required: true, "aria-invalid": !!error, "aria-describedby": error ? `${name}-error` : undefined };
  return <div className="space-y-1"><label className="block font-medium" htmlFor={name}>{label}</label>{multiline ? <textarea {...common} maxLength={props.maxLength} minLength={props.minLength} defaultValue={props.defaultValue} /> : <input {...props} {...common} />}{error && <p className="text-sm text-red-800" id={`${name}-error`}>{error}</p>}</div>;
}
export function ErrorNotice({ error }: { error: ApiError | null }) {
  return error && <div role="alert" className="rounded-lg border border-red-300 bg-red-50 p-4"><p>{error.message}</p>{error.retryAfter && <p>Retry after: {error.retryAfter}</p>}{error.traceId && <p className="break-all text-sm">Reference: {error.traceId}</p>}</div>;
}
export function asError(error: unknown) { return error instanceof ApiError ? error : new ApiError(0, "Something went wrong. Please try again."); }
