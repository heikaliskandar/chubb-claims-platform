import { DocumentCategory } from '../core/models';

export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
export const ACCEPTED_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];
export const ACCEPT_ATTRIBUTE =
  '.pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp';

export const CATEGORIES: { value: DocumentCategory; label: string; staffOnly?: boolean }[] = [
  { value: 'PHOTO', label: 'Photo of the damage' },
  { value: 'POLICE_REPORT', label: 'Police report' },
  { value: 'REPAIR_QUOTE', label: 'Repair quote' },
  { value: 'INVOICE', label: 'Invoice or receipt' },
  { value: 'PROOF_OF_OWNERSHIP', label: 'Proof of ownership' },
  { value: 'ADJUSTER_REPORT', label: 'Loss adjuster report', staffOnly: true },
  { value: 'OTHER', label: 'Other' },
];

export function categoryLabel(category: DocumentCategory): string {
  return CATEGORIES.find((option) => option.value === category)?.label ?? category;
}

export function fileProblem(file: File): string | null {
  if (file.size === 0) {
    return `${file.name} is empty.`;
  }
  if (file.size > MAX_DOCUMENT_BYTES) {
    return `${file.name} is ${formatBytes(file.size)}. The limit is 10 MB.`;
  }
  if (file.type && !ACCEPTED_TYPES.includes(file.type)) {
    return `${file.name} isn't a PDF, JPEG, PNG or WebP file.`;
  }
  return null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function iconFor(contentType: string): string {
  return contentType === 'application/pdf' ? 'picture_as_pdf' : 'image';
}

export function isImage(contentType: string): boolean {
  return contentType.startsWith('image/');
}
