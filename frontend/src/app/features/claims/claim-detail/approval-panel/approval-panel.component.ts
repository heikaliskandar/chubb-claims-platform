import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, distinctUntilChanged, filter, of, switchMap } from 'rxjs';
import { AuthService } from '../../../../core/auth.service';
import { EmployeesApiService } from '../../../../core/employees-api.service';
import { ClaimAction, ClaimDetail, EmployeeDetail } from '../../../../core/models';
import { PeopleService } from '../../../../core/people.service';
import { durationSince } from '../../../../shared/duration';

type ReviewAction = Extract<
  ClaimAction,
  'APPROVE_REFERRAL' | 'RETURN_REFERRAL' | 'REJECT_REFERRAL'
>;

interface Choice {
  action: ReviewAction;
  icon: string;
  title: string;
  description: string;
  tone: 'success' | 'neutral' | 'danger';
}

@Component({
  selector: 'app-approval-panel',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './approval-panel.component.html',
  styleUrl: './approval-panel.component.scss',
})
export class ApprovalPanelComponent {
  private readonly auth = inject(AuthService);
  private readonly employees = inject(EmployeesApiService);
  protected readonly people = inject(PeopleService);

  readonly claim = input.required<ClaimDetail>();
  readonly decide = output<ReviewAction>();

  protected readonly officer = signal<EmployeeDetail | null>(null);
  protected readonly since = durationSince;

  protected readonly referral = computed(() => this.claim().referral!);
  protected readonly isRecommender = computed(
    () => this.auth.user()?.username === this.referral().referredBy,
  );
  protected readonly officerName = computed(() => this.people.nameOf(this.referral().referredBy));
  protected readonly officerFirstName = computed(() =>
    this.isRecommender() ? 'your' : `${this.people.firstNameOf(this.referral().referredBy)}'s`,
  );
  protected readonly officerLimit = computed(() => {
    const authority = this.officer()?.approvalAuthority;
    return (
      authority?.limits.find((limit) => limit.currency === this.claim().currency)?.amount ?? null
    );
  });
  protected readonly expectedCost = computed(
    () => this.claim().assessments?.at(-1)?.assessedAmount ?? this.claim().reserveAmount,
  );
  protected readonly bars = computed(() => {
    const claim = this.claim();
    const rows = [
      { label: 'Claimed by the customer', amount: claim.claimedAmount, tone: 'muted' },
      { label: 'Expected cost (assessment)', amount: this.expectedCost(), tone: 'info' },
      { label: 'Recommended payment', amount: this.referral().amount, tone: 'strong' },
    ].filter((row): row is { label: string; amount: number; tone: string } => row.amount !== null);
    const max = Math.max(...rows.map((row) => row.amount), this.officerLimit() ?? 0, 1);
    return {
      rows: rows.map((row) => ({ ...row, width: (row.amount / max) * 100 })),
      limitAt: this.officerLimit() === null ? null : (this.officerLimit()! / max) * 100,
    };
  });
  protected readonly choices = computed<Choice[]>(() => {
    const allowed = this.claim().allowedActions;
    const name = this.people.firstNameOf(this.referral().referredBy);
    const all: Choice[] = [
      {
        action: 'APPROVE_REFERRAL',
        icon: 'check_circle',
        title: 'Approve payment',
        description: `The claim is approved at the recommended amount. ${name} then arranges payment.`,
        tone: 'success',
      },
      {
        action: 'RETURN_REFERRAL',
        icon: 'undo',
        title: 'Send back for changes',
        description: `The claim goes back to ${name} to rework. You say what needs to change.`,
        tone: 'neutral',
      },
      {
        action: 'REJECT_REFERRAL',
        icon: 'block',
        title: 'Decline the claim',
        description: 'The claim is rejected. Your reason is shared with the customer.',
        tone: 'danger',
      },
    ];
    return all.filter((choice) => allowed.includes(choice.action));
  });

  constructor() {
    this.people.load();
    toObservable(computed(() => this.claim().referral?.referredBy))
      .pipe(
        filter((username): username is string => !!username),
        distinctUntilChanged(),
        switchMap((username) =>
          this.employees
            .profile(this.isRecommender() ? 'me' : username)
            .pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((officer) => this.officer.set(officer));
  }
}
