import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ToolIconComponent } from '../../shared/components/tool-icon/tool-icon.component';
import { ToolDefinition } from '../../shared/models/tool.model';
import { ToolRegistryService } from '../../shared/services/tool-registry.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, ToolIconComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent {
  search = '';

  constructor(
    private readonly router: Router,
    readonly registry: ToolRegistryService,
  ) {
    this.registry.refresh().subscribe({ error: () => undefined });
  }

  get tools(): ToolDefinition[] {
    return this.registry.search(this.search);
  }

  get recent(): ToolDefinition[] {
    return this.registry.recent();
  }

  open(tool: ToolDefinition): void {
    this.registry.recordOpened(tool.id);
    void this.router.navigateByUrl(tool.route);
  }
}
