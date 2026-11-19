import { LabelPipe } from './label.pipe';

describe('LabelPipe', () => {
  const pipe = new LabelPipe();

  it('turns enum codes into sentence-case labels', () => {
    expect(pipe.transform('PENDING_APPROVAL')).toBe('Pending approval');
    expect(pipe.transform('SUBMITTED')).toBe('Submitted');
  });

  it('renders nothing for missing values', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
