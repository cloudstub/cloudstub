import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({ name: 'jsonHighlight' })
export class JsonHighlightPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);

  transform(value: unknown): SafeHtml {
    const json = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    const highlighted = json
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(
        /("(\\u[\da-fA-F]{4}|\\[^u]|[^"\\])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
        (match) => {
          if (/^"/.test(match)) {
            return /:$/.test(match)
              ? `<span class="json-key">${match}</span>`
              : `<span class="json-str">${match}</span>`;
          }
          if (/true|false/.test(match)) return `<span class="json-bool">${match}</span>`;
          if (/null/.test(match)) return `<span class="json-null">${match}</span>`;
          return `<span class="json-num">${match}</span>`;
        },
      );
    return this.sanitizer.bypassSecurityTrustHtml(`<code>${highlighted}</code>`);
  }
}
