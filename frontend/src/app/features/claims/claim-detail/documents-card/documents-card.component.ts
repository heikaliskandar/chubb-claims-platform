import { DatePipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/modal/modal.component';
import { Subject, merge, switchMap } from 'rxjs';
import { AuthService } from '../../../../core/auth.service';
import { ClaimsApiService } from '../../../../core/claims-api.service';
import { ClaimDocument, DocumentCategory } from '../../../../core/models';
import { PeopleService } from '../../../../core/people.service';
import { ToastService } from '../../../../core/toast.service';
import {
  ACCEPT_ATTRIBUTE,
  CATEGORIES,
  categoryLabel,
  fileProblem,
  formatBytes,
  iconFor,
  isImage,
} from '../../../../shared/documents';

@Component({
  selector: 'app-documents-card',
  imports: [DatePipe, FormsModule, ModalComponent],
  templateUrl: './documents-card.component.html',
  styleUrl: './documents-card.component.scss',
})
export class DocumentsCardComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  protected readonly people = inject(PeopleService);

  readonly claimId = input.required<string>();
  readonly canUpload = input(false);

  protected readonly accept = ACCEPT_ATTRIBUTE;
  protected readonly label = categoryLabel;
  protected readonly size = formatBytes;
  protected readonly icon = iconFor;
  protected readonly isImage = isImage;
  protected readonly isStaff = computed(() => this.auth.user()?.role !== 'CLAIMANT');
  protected readonly categories = computed(() =>
    CATEGORIES.filter((option) => this.isStaff() || !option.staffOnly),
  );

  protected readonly documents = signal<ClaimDocument[] | null>(null);
  protected readonly thumbnails = signal<Record<string, string>>({});
  protected readonly category = signal<DocumentCategory>('PHOTO');
  protected readonly progress = signal<number | null>(null);
  protected readonly problem = signal<string | null>(null);
  protected readonly dragging = signal(false);
  protected readonly removing = signal<ClaimDocument | null>(null);
  protected readonly busy = signal(false);

  private readonly refresh = new Subject<void>();
  private readonly objectUrls: string[] = [];

  constructor() {
    merge(toObservable(this.claimId), toObservable(this.canUpload), this.refresh)
      .pipe(
        switchMap(() => this.api.documents(this.claimId())),
        takeUntilDestroyed(),
      )
      .subscribe((documents) => {
        this.documents.set(documents);
        this.loadThumbnails(documents);
      });
    inject(DestroyRef).onDestroy(() => this.objectUrls.forEach((url) => URL.revokeObjectURL(url)));
  }

  protected uploaderName(document: ClaimDocument): string {
    if (document.uploadedBy === this.auth.user()?.username) {
      return 'You';
    }
    return this.isStaff() ? this.people.nameOf(document.uploadedBy) : 'Claims team';
  }

  protected onFiles(files: FileList | null): void {
    this.dragging.set(false);
    const file = files?.item(0);
    if (!file) {
      return;
    }
    const problem = fileProblem(file);
    this.problem.set(problem);
    if (problem) {
      return;
    }
    this.progress.set(0);
    this.api.uploadDocument(this.claimId(), file, this.category()).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.progress.set(Math.round((event.loaded / event.total) * 100));
        }
        if (event.type === HttpEventType.Response) {
          this.progress.set(null);
          this.toasts.show('success', 'Document added', file.name);
          this.refresh.next();
        }
      },
      error: () => this.progress.set(null),
    });
  }

  protected confirmRemove(): void {
    const document = this.removing();
    if (!document) {
      return;
    }
    this.busy.set(true);
    this.api.removeDocument(this.claimId(), document.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.removing.set(null);
        this.toasts.show('success', 'Document removed', document.fileName);
        this.refresh.next();
      },
      error: () => {
        this.busy.set(false);
        this.removing.set(null);
        this.refresh.next();
      },
    });
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.onFiles(event.dataTransfer?.files ?? null);
  }

  protected open(document: ClaimDocument, download: boolean): void {
    this.api.documentContent(this.claimId(), document.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      if (download) {
        const link = Object.assign(window.document.createElement('a'), {
          href: url,
          download: document.fileName,
        });
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      } else {
        this.objectUrls.push(url);
        window.open(url, '_blank', 'noopener');
      }
    });
  }

  private loadThumbnails(documents: ClaimDocument[]): void {
    documents
      .filter((document) => isImage(document.contentType) && !this.thumbnails()[document.id])
      .forEach((document) =>
        this.api.documentContent(this.claimId(), document.id).subscribe((blob) => {
          const url = URL.createObjectURL(blob);
          this.objectUrls.push(url);
          this.thumbnails.update((thumbnails) => ({ ...thumbnails, [document.id]: url }));
        }),
      );
  }
}
