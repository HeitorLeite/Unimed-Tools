/**
 * Catálogo visual das ferramentas e seus estados de disponibilidade.
 */
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import {
  ToolIconComponent,
  ToolIconName,
} from '../../shared/components/tool-icon/tool-icon.component';
import { AuthService } from '../../shared/services/auth.service';

interface Category {
  id: string;
  label: string;
  tag: string;
  desc: string;
  route: string;
  accepts: string;
  icon: ToolIconName;
  ready: boolean;
  permission?: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [NgFor, NgIf, ToolIconComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent {
  constructor(
    private router: Router,
    private readonly auth: AuthService,
  ) {}

  categories: Category[] = [
    {
      id: 'relatorios',
      label: 'Consultas SGU',
      tag: 'Central de relatórios',
      desc: 'Executa relatórios cadastrados no SGU, monta os filtros automaticamente, exibe os resultados e exporta em CSV, TXT ou XLSX.',
      route: '/relatorios',
      accepts: 'CSV · TXT · XLSX',
      ready: true,
      permission: 'RELATORIOS_ACESSAR',
      icon: 'relatorios',
    },
    {
      id: 'xml',
      label: 'XML TISS',
      tag: 'Correção e validação',
      desc: 'Corrige prefixos incorretos e remove blocos com valorTotal zerado em arquivos XML TISS. Suporta processamento em lote com detecção de guias duplicadas.',
      route: '/xml/ferramentas',
      accepts: '.xml',
      ready: true,
      permission: 'XML_ACESSAR',
      icon: 'xml',
    },
    {
      id: 'bi',
      label: 'Business Intelligence',
      tag: 'Especialidade médica',
      desc: 'Preenche automaticamente a coluna de especialidade médica em planilhas de despesas usando planilha de médicos como referência e mapa TUSS.',
      route: '/bi/especialidade-medica',
      accepts: '.xlsx recomendado',
      ready: true,
      permission: 'BI_ACESSAR',
      icon: 'bi',
    },
    {
      id: 'ans',
      label: 'Agência Nacional de Saúde',
      tag: 'Corretor de rede RPS',
      desc: 'Filtra arquivos TXT posicionais com base em erros de CNES, CNPJ, Município, Prestador ou Aviso a partir de planilha de referência.',
      route: '/ans/corretor-rede',
      accepts: '.txt + .xlsx',
      ready: true,
      permission: 'ANS_ACESSAR',
      icon: 'ans',
    },
    {
      id: 'fechamento',
      label: 'Produção',
      tag: 'Fechamento',
      desc: 'Converte a planilha de Eventos para Fechamento da Produção dos Prestadores para o formato CSV esperado pelo sistema.',
      route: '/fechamento/corretor',
      accepts: '.xlsx → .csv',
      ready: false,
      permission: 'APLICACAO_ACESSAR',
      icon: 'fechamento',
    },
  ];

  navigate(cat: Category) {
    if (cat.ready) this.router.navigate([cat.route]);
  }

  navigateFirstAvailable() {
    const category = this.accessibleCategories.find((item) => item.ready);
    if (category) this.navigate(category);
  }

  get availableCategories() {
    return this.accessibleCategories.filter((category) => category.ready).length;
  }

  get accessibleCategories() {
    return this.categories.filter(
      (category) => !category.permission || this.auth.hasPermission(category.permission),
    );
  }
}
