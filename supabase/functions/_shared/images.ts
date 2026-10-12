// Image validation for CDN uploads. Contract: docs/CONTRACTS.md section 3.

export type ImageType = "webp" | "jpg" | "png";

export type Variant = "thumb" | "preview" | "full" | "original";

export const VARIANT_RULES: Record<Variant, { types: ImageType[]; maxBytes: number; required: boolean }> = {
  thumb:    { types: ["webp"],       maxBytes: 300 * 1024,       required: true },
  preview:  { types: ["webp"],       maxBytes: 1.5 * 1024 * 1024, required: true },
  full:     { types: ["webp"],       maxBytes: 6 * 1024 * 1024,   required: true },
  original: { types: ["jpg", "png"], maxBytes: 15 * 1024 * 1024,  required: false },
};

/** Detects the real type from magic bytes (never trust the file name / MIME). */
export function detectImageType(bytes: Uint8Array): ImageType | null {
  if (bytes.length >= 12 &&
      bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&   // RIFF
      bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) { // WEBP
    return "webp";
  }
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return "jpg";
  }
  if (bytes.length >= 8 &&
      bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 &&
      bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a) {
    return "png";
  }
  return null;
}

export interface ValidFile {
  variant: Variant;
  type: ImageType;
  bytes: Uint8Array;
}

/** Validates one variant; returns an error message or the validated file. */
export function validateVariant(variant: Variant, bytes: Uint8Array | null): ValidFile | string | null {
  const rule = VARIANT_RULES[variant];
  if (!bytes || bytes.length === 0) {
    return rule.required ? `${variant} is required` : null;
  }
  if (bytes.length > rule.maxBytes) {
    return `${variant} is ${(bytes.length / 1048576).toFixed(2)} MB; max is ${(rule.maxBytes / 1048576).toFixed(2)} MB`;
  }
  const type = detectImageType(bytes);
  if (!type || !rule.types.includes(type)) {
    return `${variant} must be ${rule.types.join(" or ")} (got ${type ?? "unknown"})`;
  }
  return { variant, type, bytes };
}

export function fileName(f: ValidFile): string {
  return f.variant === "original" ? `original.${f.type}` : `${f.variant}.webp`;
}

/** 'w/' + 10 random hex chars, e.g. 'w/7f3a9c2e1b' */
export function newStorageKey(): string {
  const b = crypto.getRandomValues(new Uint8Array(5));
  return "w/" + Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
}

export const STORAGE_KEY_RE = /^w\/[0-9a-f]{10}$/;
