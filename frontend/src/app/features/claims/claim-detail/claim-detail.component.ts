import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { ClaimAction, ClaimDetail } from '../../../core/models';
import { PeopleService } from '../../../core/people.service';
import { ToastService } from '../../../core/toast.service';
import { Crumb, PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { LabelPipe } from '../../../shared/label.pipe';
import { marketName } from '../../../shared/markets';
import { ModalComponent } from '../../../shared/modal/modal.component';
import { poll } from '../../../shared/polling';
import { StatusTagComponent } from '../../../shared/status-tag/status-tag.component';
import { ApprovalPanelComponent } from './approval-panel/approval-panel.component';

interface ActionConfig {
  label: string;
  icon: string;
  style: 'btn-primary' | 'btn-success' | 'btn-outline' | 'btn-danger';
  text?: { label: string; optional?: boolean };
  amount?: { label: string; allowZero?: boolean };
  hint?: string;
}

const ACTIONS: Record<ClaimAction, ActionConfig> = {
  ASSIGN_TO_ME: { label: 'Assign to me', icon: 'person_add', style: 'btn-primary' },
  START_REVIEW: { label: 'Start review', icon: 'play_arrow', style: 'btn-primary' },
  REQUEST_INFORMATION: {
    label: 'Request info',
    icon: 'help',
    style: 'btn-outline',
    text: { label: 'Question' },
  },
  RESPOND_TO_INFORMATION_REQUEST: {
    label: 'Respond',
    icon: 'reply',
    style: 'btn-primary',
    text: { label: 'Answer' },
  },
  ASSESS: {
    label: 'Assess',
    icon: 'calculate',
    style: 'btn-outline',
    amount: { label: 'Expected cost (reserve)', allowZero: true },
    text: { label: 'Notes', optional: true },
  },
  APPROVE: {
    label: 'Approve',
    icon: 'check',
    style: 'btn-success',
    amount: { label: 'Approved amount' },
    text: { label: 'Reason' },
  },
  REJECT: { label: 'Reject', icon: 'close', style: 'btn-danger', text: { label: 'Reason' } },
  APPROVE_REFERRAL: {
    label: 'Approve payment',
    icon: 'check_circle',
    style: 'btn-success',
    text: { label: 'Note for the officer', optional: true },
    hint: 'The claim will be approved at the recommended amount, and the officer can then arrange payment.',
  },
  RETURN_REFERRAL: {
    label: 'Send back for changes',
    icon: 'undo',
    style: 'btn-outline',
    text: { label: 'What should the officer change?' },
    hint: 'The claim goes back to the officer for review. Nothing is approved or rejected yet.',
  },
  REJECT_REFERRAL: {
    label: 'Decline the claim',
    icon: 'block',
    style: 'btn-danger',
    text: { label: 'Reason (shared with the customer)' },
    hint: 'The claim will be rejected and the customer will see your reason.',
  },
  SETTLE: { label: 'Mark settled', icon: 'account_balance_wallet', style: 'btn-success' },
};

const REVIEW_ACTIONS: ClaimAction[] = ['APPROVE_REFERRAL', 'RETURN_REFERRAL', 'REJECT_REFERRAL'];

@Component({
  selector: 'app-claim-detail',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    LabelPipe,
    ModalComponent,
    PageHeaderComponent,
    StatusTagComponent,
    ApprovalPanelComponent,
  ],
  templateUrl: './claim-detail.component.html',
  styleUrl: './claim-detail.component.scss',
})
export class ClaimDetailComponent {
  private readonly api = inject(ClaimsApiService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  protected readonly people = inject(PeopleService);

  readonly id = input.required<string>();

  protected readonly actions = ACTIONS;
  protected readonly marketName = marketName;
  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly activeAction = signal<ClaimAction | null>(null);
  protected readonly busy = signal(false);
  protected readonly text = new FormControl('', { nonNullable: true });
  protected readonly amount = new FormControl<number | null>(null);

  protected readonly openRequest = computed(() =>
    this.claim()?.informationRequests.find((request) => request.status === 'OPEN'),
  );
  protected readonly isStaff = computed(() => this.auth.user()?.role !== 'CLAIMANT');
  protected readonly headerActions = computed(() =>
    (this.claim()?.allowedActions ?? []).filter((action) => !REVIEW_ACTIONS.includes(action)),
  );
  protected readonly showApprovalPanel = computed(() => {
    const claim = this.claim();
    return this.isStaff() && claim?.status === 'PENDING_APPROVAL' && claim.referral !== null;
  });
  protected readonly crumbs = computed<Crumb[]>(() => {
    const isClaimant = this.auth.user()?.role === 'CLAIMANT';
    return [
      isClaimant
        ? { label: 'My claims', routerLink: '/claims/active' }
        : { label: 'Claims', routerLink: '/claims/open' },
      { label: this.claim()?.claimNumber ?? '' },
    ];
  });

  constructor() {
    if (this.isStaff()) {
      this.people.load();
    }
    toObservable(this.id)
      .pipe(
        switchMap((id) => poll(() => this.api.get(id))),
        takeUntilDestroyed(),
      )
      .subscribe((claim) => this.claim.set(claim));
  }

  protected choose(action: ClaimAction): void {
    const config = ACTIONS[action];
    if (!config.text && !config.amount) {
      this.run(action);
      return;
    }
    this.text.reset('');
    this.amount.reset(this.suggestedAmount(action));
    this.activeAction.set(action);
  }

  protected canSubmit(action: ClaimAction): boolean {
    const config = ACTIONS[action];
    const textOk = !config.text || config.text.optional || this.text.value.trim().length > 0;
    const value = this.amount.value;
    const amountOk =
      !config.amount || (value !== null && (config.amount.allowZero ? value >= 0 : value > 0));
    return textOk && amountOk && !this.busy();
  }

  protected run(action: ClaimAction): void {
    const claim = this.claim();
    if (!claim) {
      return;
    }
    this.busy.set(true);
    this.request(action, claim).subscribe({
      next: (updated) => {
        this.claim.set(updated);
        this.activeAction.set(null);
        this.busy.set(false);
        this.toasts.show('success', ACTIONS[action].label, updated.nextStep);
      },
      error: () => this.busy.set(false),
    });
  }

  private request(action: ClaimAction, claim: ClaimDetail): Observable<ClaimDetail> {
    const text = this.text.value.trim();
    const amount = this.amount.value ?? 0;
    switch (action) {
      case 'ASSIGN_TO_ME':
        return this.api.assign(claim.id);
      case 'START_REVIEW':
        return this.api.startReview(claim.id);
      case 'REQUEST_INFORMATION':
        return this.api.requestInformation(claim.id, text);
      case 'RESPOND_TO_INFORMATION_REQUEST':
        return this.api.respond(claim.id, this.openRequest()!.id, text);
      case 'ASSESS':
        return this.api.assess(claim.id, amount, text);
      case 'APPROVE':
        return this.api.approve(claim.id, amount, text);
      case 'REJECT':
        return this.api.reject(claim.id, text);
      case 'APPROVE_REFERRAL':
        return this.api.reviewReferral(claim.id, 'APPROVE', text);
      case 'RETURN_REFERRAL':
        return this.api.reviewReferral(claim.id, 'RETURN', text);
      case 'REJECT_REFERRAL':
        return this.api.reviewReferral(claim.id, 'REJECT', text);
      case 'SETTLE':
        return this.api.settle(claim.id);
    }
  }

  private suggestedAmount(action: ClaimAction): number | null {
    const claim = this.claim();
    if (action === 'ASSESS') {
      return claim?.claimedAmount ?? null;
    }
    if (action === 'APPROVE') {
      return claim?.assessments?.at(-1)?.assessedAmount ?? null;
    }
    return null;
  }
}
