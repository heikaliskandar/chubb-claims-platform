import { computed, Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'claims.login-image';
const MAX_EDGE = 1600;
const DEFAULT_LOGIN_IMAGE = 'images/login-image-default.webp';

@Injectable({ providedIn: 'root' })
export class BrandingService {
  readonly customLoginImage = signal<string | null>(readStored());
  readonly loginImage = computed(() => this.customLoginImage() ?? DEFAULT_LOGIN_IMAGE);

  async setLoginImage(file: File): Promise<void> {
    const dataUrl = await toScaledJpeg(file);
    localStorage.setItem(STORAGE_KEY, dataUrl);
    this.customLoginImage.set(dataUrl);
  }

  resetLoginImage(): void {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {}
    this.customLoginImage.set(null);
  }
}

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

async function toScaledJpeg(file: File): Promise<string> {
  const image = await createImageBitmap(file);
  const scale = Math.min(1, MAX_EDGE / Math.max(image.width, image.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(image.width * scale);
  canvas.height = Math.round(image.height * scale);
  canvas.getContext('2d')!.drawImage(image, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL('image/jpeg', 0.82);
}
