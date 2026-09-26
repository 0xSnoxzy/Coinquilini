import { Store } from "../core/store";
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  inject,
  OnDestroy,
  Output,
  ViewChild,
} from "@angular/core";
@Component({
  selector: "cq-modal",
  standalone: true,
  template: `<dialog
    #dialog
    class="modal"
    aria-labelledby="cq-dialog-title"
    (cancel)="$event.preventDefault(); closed.emit()"
  >
    <header>
      <h2 id="cq-dialog-title">{{ title }}</h2>
      <button
        type="button"
        class="icon-button"
        aria-label="Chiudi finestra"
        (click)="closed.emit()"
      >
        ✕
      </button>
    </header>
    <div class="modal-body">
      @if (s.error()) {
        <p class="form-error" role="alert">{{ s.error() }}</p>
      }
      <ng-content />
    </div>
  </dialog>`,
})
export class Modal implements AfterViewInit, OnDestroy {
  s = inject(Store);
  @Input() title = "";
  @Output() closed = new EventEmitter<void>();
  @ViewChild("dialog") dialog!: ElementRef<HTMLDialogElement>;
  ngOnInit() {
    this.s.error.set("");
  }
  ngAfterViewInit() {
    this.dialog.nativeElement.showModal();
  }
  ngOnDestroy() {
    this.dialog.nativeElement.close();
  }
}
