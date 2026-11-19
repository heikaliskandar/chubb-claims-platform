import { fileProblem, formatBytes, MAX_DOCUMENT_BYTES } from './documents';

describe('document checks', () => {
  const file = (name: string, type: string, size: number) =>
    new File([new Uint8Array(size)], name, { type });

  it('accepts PDFs and images up to 10 MB', () => {
    expect(fileProblem(file('quote.pdf', 'application/pdf', 2048))).toBeNull();
    expect(fileProblem(file('photo.jpg', 'image/jpeg', MAX_DOCUMENT_BYTES))).toBeNull();
  });

  it('explains what is wrong with a file before it is uploaded', () => {
    expect(fileProblem(file('empty.pdf', 'application/pdf', 0))).toBe('empty.pdf is empty.');
    expect(fileProblem(file('huge.pdf', 'application/pdf', MAX_DOCUMENT_BYTES + 1))).toContain(
      'The limit is 10 MB',
    );
    expect(fileProblem(file('notes.docx', 'application/msword', 10))).toContain(
      "isn't a PDF, JPEG, PNG or WebP",
    );
  });

  it('formats sizes for people', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(21_835)).toBe('21 KB');
    expect(formatBytes(3.4 * 1024 * 1024)).toBe('3.4 MB');
  });
});
