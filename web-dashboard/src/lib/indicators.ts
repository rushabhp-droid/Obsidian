import type { IndicatorType } from "./types";

// Client-side checks are for error copy only. Spring validates again, and the
// daemon parses every value as IpAddr / strict domain before touching the OS.

const DOMAIN = /^(?=.{1,253}$)(?:(?!-)[a-z0-9-]{1,63}(?<!-)\.)+[a-z]{2,63}$/;
const IPV4 = /^(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?:\.(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^\+?\d{6,15}$/;
const SHA256 = /^[a-f0-9]{64}$/;

/** Lowercase, trimmed; phone numbers lose spaces, dashes and brackets. */
export function normalizeIndicator(type: IndicatorType, raw: string) {
  const value = raw.trim().toLowerCase();
  return type === "PHONE" ? value.replace(/[\s\-()]/g, "") : value;
}

function isIpv6(value: string) {
  if (!value.includes(":") || !/^[0-9a-f:.]+$/.test(value)) return false;
  try {
    new URL(`http://[${value}]/`);
    return true;
  } catch {
    return false;
  }
}

/** Returns an explicit error message, or null when the value is acceptable. */
export function validateIndicator(type: IndicatorType, raw: string): string | null {
  const value = normalizeIndicator(type, raw);
  if (!value) return "Enter a value.";
  switch (type) {
    case "DOMAIN":
      return DOMAIN.test(value) ? null : "Enter a domain such as login-verify.example, without a scheme or path.";
    case "IP":
      return IPV4.test(value) || isIpv6(value) ? null : "Enter a single IPv4 or IPv6 address, without a port or range.";
    case "URL":
      try {
        const url = new URL(value);
        return url.protocol === "http:" || url.protocol === "https:" ? null : "Only http and https URLs can be listed.";
      } catch {
        return "Enter a full URL, including http:// or https://.";
      }
    case "EMAIL":
      return EMAIL.test(value) ? null : "Enter a sender address such as billing@invoice-verify.example.";
    case "PHONE":
      return PHONE.test(value) ? null : "Enter a phone number with country code, digits only, such as +15550100.";
    case "FILE_HASH":
      return SHA256.test(value) ? null : "Enter a SHA-256 hash: 64 hexadecimal characters.";
  }
}

/** Only these reach the Rust daemons (/ws/firewall carries IP | DOMAIN). */
export function isNetworkIndicator(type: IndicatorType) {
  return type === "DOMAIN" || type === "IP";
}
