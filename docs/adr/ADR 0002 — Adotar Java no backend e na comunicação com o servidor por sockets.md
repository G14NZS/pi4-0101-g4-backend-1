## Status: aceito

##### Contexto:
O Reservice utilizará um servidor desenvolvido em Java que realizará comunicação por sockets e utilizará threads para tratar conexões e requisições. A equipe também está estudando Java durante o curso e precisa desenvolver a camada de backend responsável pelas regras de negócio e pela comunicação com esse servidor. A adoção de tecnologias conhecidas é relevante devido ao prazo e à experiência atual da equipe.

##### Decisão:
Adotar Java para o desenvolvimento do backend e do servidor de comunicação do Reservice, utilizando sockets para a troca de dados e threads para o tratamento das conexões.

##### Alternativas consideradas:
- Node.js no backend: possui bom suporte para aplicações de rede e operações assíncronas, mas foi descartado porque introduziria outra linguagem e outro ambiente de execução em uma equipe que atualmente está concentrando seus estudos em Java.
- Python no backend: possui bibliotecas simples para comunicação em rede, mas foi descartado pelo mesmo motivo: adicionaria uma nova tecnologia sem oferecer, no contexto atual do projeto, uma vantagem que compensasse o aumento da curva de aprendizado.
- Comunicação HTTP/REST: seria uma alternativa comum para comunicação entre aplicações, porém o projeto utilizará sockets e threads como parte dos requisitos e conteúdos trabalhados na disciplina, tornando a comunicação por sockets mais adequada ao contexto acadêmico atual.

##### Consequências:
- Positivas: reduz a quantidade de linguagens utilizadas no lado servidor; aproveita o conhecimento que a equipe está adquirindo na disciplina; facilita o compartilhamento de conceitos, classes utilitárias e padrões entre os componentes escritos em Java; permite aplicar diretamente os conteúdos de sockets, threads e programação concorrente estudados no curso.
- Negativas: a equipe ficará mais dependente do ecossistema Java; o uso direto de sockets exige definir e manter um protocolo de comunicação entre os componentes; o uso de múltiplas threads introduz preocupações com concorrência, sincronização, tratamento de falhas e gerenciamento de conexões.