import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    const words = value.toLowerCase().replaceAll('_', ' ');
    return words.charAt(0).toUpperCase() + words.slice(1);
  }
}
