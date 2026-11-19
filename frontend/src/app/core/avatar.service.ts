import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'claims.avatars';
const AVATAR_SIZE = 160;

@Injectable({ providedIn: 'root' })
export class AvatarService {
  private readonly avatars = signal<Record<string, string>>(readStored());

  avatarOf(username: string | null | undefined): string | null {
    return username ? (this.avatars()[username] ?? null) : null;
  }

  async upload(username: string, file: File): Promise<void> {
    const dataUrl = await toSquareJpeg(file);
    this.avatars.update((avatars) => ({ ...avatars, [username]: dataUrl }));
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.avatars()));
    } catch {
      // Storage can be unavailable (private browsing, quota); the photo still shows this session.
    }
  }
}

function readStored(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
  } catch {
    return {};
  }
}

async function toSquareJpeg(file: File): Promise<string> {
  const image = await createImageBitmap(file);
  const side = Math.min(image.width, image.height);
  const canvas = document.createElement('canvas');
  canvas.width = AVATAR_SIZE;
  canvas.height = AVATAR_SIZE;
  canvas
    .getContext('2d')!
    .drawImage(
      image,
      (image.width - side) / 2,
      (image.height - side) / 2,
      side,
      side,
      0,
      0,
      AVATAR_SIZE,
      AVATAR_SIZE,
    );
  return canvas.toDataURL('image/jpeg', 0.85);
}
