import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface FusexSpaValidacao {
  guias: string[];
  quantidadeGuias: number;
  execucaoDisponivel: boolean;
  mensagem: string;
}

@Injectable({ providedIn: 'root' })
export class FusexSpaService {
  private readonly url = `${environment.apiUrl}/fusex-spa`;
  constructor(private readonly http: HttpClient) {}
  validar(guias: string) {
    return this.http.post<FusexSpaValidacao>(`${this.url}/validar`, { guias });
  }
  executar(guias: string) {
    return this.http.post<void>(`${this.url}/executar`, { guias, confirmado: true });
  }
}
