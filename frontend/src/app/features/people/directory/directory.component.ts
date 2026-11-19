import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { combineLatest, debounceTime, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { EmployeesApiService } from '../../../core/employees-api.service';
import { EmployeeSummary, PageResponse } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { EmployeeStatusTagComponent } from '../../../shared/employee-status-tag/employee-status-tag.component';
import { LabelPipe } from '../../../shared/label.pipe';
import { marketName } from '../../../shared/markets';
import { OrgNode, OrgTreeComponent } from '../../../shared/org-tree/org-tree.component';
import { PagerComponent } from '../../../shared/pager/pager.component';
import { UserAvatarComponent } from '../../../shared/user-avatar/user-avatar.component';

type View = 'list' | 'chart';

const PAGE_SIZE = 10;
const CHART_SIZE = 100;

@Component({
  selector: 'app-directory',
  imports: [
    DatePipe,
    LabelPipe,
    PageHeaderComponent,
    EmployeeStatusTagComponent,
    OrgTreeComponent,
    PagerComponent,
    UserAvatarComponent,
  ],
  templateUrl: './directory.component.html',
  styleUrl: './directory.component.scss',
})
export class DirectoryComponent {
  private readonly api = inject(EmployeesApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly marketName = marketName;
  protected readonly view = signal<View>('list');
  protected readonly query = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly result = signal<PageResponse<EmployeeSummary> | null>(null);

  protected readonly orgChart = computed<OrgNode[]>(() => {
    const people = this.result()?.content ?? [];
    const root = people.find((person) => person.username === this.auth.user()?.username);
    return root && this.view() === 'chart' ? [toNode(root, people)] : [];
  });

  constructor() {
    combineLatest([
      toObservable(this.query).pipe(debounceTime(250)),
      toObservable(this.pageIndex),
      toObservable(this.view),
    ])
      .pipe(
        switchMap(([query, page, view]) =>
          view === 'chart'
            ? this.api.directory('', 0, CHART_SIZE)
            : this.api.directory(query, page, PAGE_SIZE),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => this.result.set(result));
  }

  protected showView(view: View): void {
    if (view === this.view()) {
      return;
    }
    this.result.set(null);
    this.view.set(view);
  }

  protected search(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
    this.pageIndex.set(0);
  }

  protected open(username: string): void {
    this.router.navigate(['/people', username]);
  }
}

function toNode(person: EmployeeSummary, people: EmployeeSummary[]): OrgNode {
  return {
    person,
    children: people
      .filter((candidate) => candidate.managerUsername === person.username)
      .map((report) => toNode(report, people)),
  };
}
