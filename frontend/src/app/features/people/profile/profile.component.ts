import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { ClaimsApiService } from '../../../core/claims-api.service';
import { EmployeesApiService } from '../../../core/employees-api.service';
import { ClaimSummary, EmployeeDetail, PageResponse } from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { Crumb, PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { ClaimTableComponent } from '../../../shared/claim-table/claim-table.component';
import { EmployeeStatusTagComponent } from '../../../shared/employee-status-tag/employee-status-tag.component';
import { LabelPipe } from '../../../shared/label.pipe';
import { marketName } from '../../../shared/markets';
import { ModalComponent } from '../../../shared/modal/modal.component';
import { UserAvatarComponent } from '../../../shared/user-avatar/user-avatar.component';

type Tab = 'overview' | 'team' | 'claims' | 'authority';

const TABS: { value: Tab; label: string; icon: string }[] = [
  { value: 'overview', label: 'Overview', icon: 'person' },
  { value: 'team', label: 'Team', icon: 'group' },
  { value: 'claims', label: 'Claims', icon: 'inbox' },
  { value: 'authority', label: 'Authority', icon: 'shield' },
];

const CLAIMS_PAGE_SIZE = 5;

@Component({
  selector: 'app-profile',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    LabelPipe,
    PageHeaderComponent,
    ClaimTableComponent,
    EmployeeStatusTagComponent,
    ModalComponent,
    UserAvatarComponent,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent {
  private readonly employees = inject(EmployeesApiService);
  private readonly claimsApi = inject(ClaimsApiService);
  private readonly toasts = inject(ToastService);

  readonly username = input.required<string>();

  protected readonly tabs = TABS;
  protected readonly claimsPageSize = CLAIMS_PAGE_SIZE;
  protected readonly marketName = marketName;
  protected readonly tab = signal<Tab>('overview');
  protected readonly employee = signal<EmployeeDetail | null>(null);
  protected readonly claims = signal<PageResponse<ClaimSummary> | null>(null);
  protected readonly claimsPage = signal(0);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly contactForm = inject(FormBuilder).nonNullable.group({
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[+0-9 ()-]*$/)]],
    officeLocation: ['', Validators.maxLength(80)],
  });

  protected readonly crumbs = computed<Crumb[]>(() => {
    const employee = this.employee();
    return employee?.self
      ? [{ label: 'My profile' }]
      : [{ label: 'People', routerLink: '/people' }, { label: employee?.fullName ?? '' }];
  });

  protected readonly tenure = computed(() => {
    const joined = this.employee()?.joinDate;
    if (!joined) {
      return '';
    }
    const start = new Date(joined);
    const now = new Date();
    const months =
      (now.getFullYear() - start.getFullYear()) * 12 + now.getMonth() - start.getMonth();
    const years = Math.floor(months / 12);
    return years > 0 ? `${years}y ${months % 12}m` : `${months}m`;
  });

  constructor() {
    toObservable(this.username)
      .pipe(
        switchMap((username) => this.employees.profile(username)),
        takeUntilDestroyed(),
      )
      .subscribe((employee) => {
        this.employee.set(employee);
        this.claimsPage.set(0);
        this.tab.set('overview');
      });

    toObservable(computed(() => ({ employee: this.employee()?.username, page: this.claimsPage() })))
      .pipe(
        filter(({ employee }) => employee !== undefined),
        switchMap(({ employee, page }) =>
          this.claimsApi.queue({ assignedTo: employee, page, size: CLAIMS_PAGE_SIZE }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((claims) => this.claims.set(claims));
  }

  protected openEdit(employee: EmployeeDetail): void {
    this.contactForm.setValue({
      phone: employee.phone ?? '',
      officeLocation: employee.officeLocation ?? '',
    });
    this.editing.set(true);
  }

  protected saveContact(): void {
    const { phone, officeLocation } = this.contactForm.getRawValue();
    this.saving.set(true);
    this.employees.updateMyContact(phone, officeLocation).subscribe({
      next: (employee) => {
        this.employee.set(employee);
        this.editing.set(false);
        this.saving.set(false);
        this.toasts.show('success', 'Contact details saved');
      },
      error: () => this.saving.set(false),
    });
  }
}
