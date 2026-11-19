import { Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { EmployeeStatus } from '../../core/models';
import { LabelPipe } from '../label.pipe';

const SEVERITY: Record<EmployeeStatus, 'success' | 'warn' | 'secondary'> = {
  ACTIVE: 'success',
  ON_LEAVE: 'warn',
  INACTIVE: 'secondary',
};

@Component({
  selector: 'app-employee-status-tag',
  imports: [TagModule, LabelPipe],
  template: `<p-tag [value]="status() | label" [severity]="severity()" [rounded]="true" />`,
})
export class EmployeeStatusTagComponent {
  readonly status = input.required<EmployeeStatus>();

  protected readonly severity = computed(() => SEVERITY[this.status()]);
}
