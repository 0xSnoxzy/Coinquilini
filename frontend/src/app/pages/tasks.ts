import { Component, inject, computed } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
import { Modal } from "../shared/modal";
import { Payload, Turn } from "../core/models";
import { today } from "../core/dates";
// Responsabilità suggerita: Giovanna: definizioni delle mansioni e singole occorrenze dei turni.
@Component({
  selector: "cq-tasks",
  standalone: true,
  imports: [FormsModule, Modal],
  template: `<div class="page-title">
      <div>
        <h1>Le mansioni della casa.</h1>
        <p>Compiti singoli o ricorrenti, assegnati a mano o a rotazione.</p>
      </div>
      <button class="primary" (click)="edit()">＋ Nuova mansione</button>
    </div>
    <div class="grid two">
      @for (turn of pending(); track turn.id) {
        <article class="panel task-card">
          <div class="row no-border">
            <span class="emoji">{{
              turn.done
                ? turn.emojiSnapshot || task(turn.taskId)?.emoji
                : task(turn.taskId)?.emoji
            }}</span>
            <div>
              <h2>
                {{
                  turn.done
                    ? turn.titleSnapshot || task(turn.taskId)?.title
                    : task(turn.taskId)?.title
                }}
              </h2>
              <small>{{ s.name(turn.userId) }} · {{ s.date(turn.due) }}</small>
            </div>
          </div>
          <p class="muted preserve-lines">{{ task(turn.taskId)?.notes }}</p>
          <div class="actions">
            <span class="pill">{{
              task(turn.taskId)?.recurrence === "WEEKLY"
                ? "Ogni settimana"
                : "Una volta"
            }}</span
            ><span class="pill">{{
              task(turn.taskId)?.mode === "AUTO"
                ? "Rotazione automatica"
                : "Assegnazione manuale"
            }}</span>
          </div>
          <div class="actions top-gap">
            @if (s.mine(turn.userId) || s.owner()) {
              <button
                class="primary"
                [disabled]="s.busy()"
                (click)="
                  s.send(
                    'tasks/complete',
                    { id: turn.id },
                    'Turno completato; la programmazione è aggiornata.'
                  )
                "
              >
                ✓ Fatto
              </button>
            }
            <button (click)="edit(turn)">Modifica</button
            ><button
              class="icon-button"
              aria-label="Rimuovi mansione"
              (click)="remove(turn.taskId)"
            >
              ×
            </button>
          </div>
        </article>
      } @empty {
        <section class="panel empty">
          Nessuna mansione in programma. Aggiungi il primo compito.
        </section>
      }
    </div>
    <section class="panel top-gap">
      <h2>Storico dei turni</h2>
      @for (turn of history(); track turn.id) {
        <div class="row">
          <span class="emoji">{{
            turn.done
              ? turn.emojiSnapshot || task(turn.taskId)?.emoji
              : task(turn.taskId)?.emoji
          }}</span>
          <div class="grow">
            <strong>{{
              turn.done
                ? turn.titleSnapshot || task(turn.taskId)?.title
                : task(turn.taskId)?.title
            }}</strong
            ><small
              >{{ s.name(turn.userId) }} · scadenza
              {{ s.date(turn.due) }}</small
            >
          </div>
          <span class="pill">Completato</span>
        </div>
      } @empty {
        <p class="empty">
          I turni completati rimarranno qui anche dopo la rimozione della
          mansione.
        </p>
      }
    </section>
    @if (draft) {
      <cq-modal
        [title]="draft['id'] ? 'Modifica mansione' : 'Nuova mansione'"
        (closed)="draft = null"
        ><form #f="ngForm" (ngSubmit)="save()">
          <label
            >Nome<input
              name="title"
              [(ngModel)]="draft['title']"
              placeholder="Pulisci giardino"
              required
              maxlength="100" /></label
          ><label
            >Descrizione<textarea
              name="notes"
              [(ngModel)]="draft['notes']"
              maxlength="500"
              rows="3"
            ></textarea>
          </label>
          <div class="form-grid">
            <label
              >Emoji<select name="emoji" [(ngModel)]="draft['emoji']">
                @for (e of emojis; track e) {
                  <option>{{ e }}</option>
                }
              </select></label
            ><label
              >Scadenza<input
                type="date"
                name="due"
                [(ngModel)]="draft['due']"
                required /></label
            ><label
              >Assegnata a<select name="userId" [(ngModel)]="draft['userId']">
                @for (m of s.activeMembers(); track m.id) {
                  <option [ngValue]="m.id">{{ m.name }}</option>
                }
              </select></label
            ><label
              >Ripetizione<select
                name="recurrence"
                [(ngModel)]="draft['recurrence']"
              >
                <option value="ONCE">Una volta</option>
                <option value="WEEKLY">Ogni settimana</option>
              </select></label
            >
          </div>
          @if (draft["recurrence"] === "WEEKLY") {
            <label
              >Prossimi turni<select name="mode" [(ngModel)]="draft['mode']">
                <option value="MANUAL">Resta alla persona assegnata</option>
                <option value="AUTO">Ruota tra i coinquilini</option>
              </select></label
            ><small
              >La rotazione segue l’ordine dei coinquilini. Puoi modificare ogni
              turno futuro.</small
            >
          }
          <button class="primary" [disabled]="f.invalid || s.busy()">
            Salva mansione
          </button>
        </form></cq-modal
      >
    }`,
})
export class Tasks {
  s = inject(Store);
  draft: Payload | null = null;
  emojis = ["🧹", "🧽", "🫧", "♻️", "🌿", "🪴", "🧺", "🔧", "🐈"];
  task(id: number) {
    return this.s.data()!.tasks.find((t) => t.id === id);
  }
  pending = computed(() =>
    this.s
      .data()!
      .turns.filter((t) => !t.done && this.task(t.taskId)?.active)
      .sort((a, b) => a.due.localeCompare(b.due)),
  );
  history = computed(() =>
    this.s
      .data()!
      .turns.filter((t) => t.done)
      .sort((a, b) => b.completedAt.localeCompare(a.completedAt)),
  );
  edit(turn?: Turn) {
    this.draft = turn
      ? { ...this.task(turn.taskId), userId: turn.userId, due: turn.due }
      : {
          title: "",
          notes: "",
          emoji: "🧹",
          userId: this.s.user()!.id,
          due: today(),
          recurrence: "ONCE",
          mode: "MANUAL",
        };
  }
  async save() {
    if (await this.s.send("tasks/save", this.draft!)) this.draft = null;
  }
  remove(id: number) {
    if (
      confirm(
        "Rimuovere la mansione e i turni futuri? Lo storico resterà disponibile.",
      )
    )
      void this.s.send("tasks/delete", { id });
  }
}
