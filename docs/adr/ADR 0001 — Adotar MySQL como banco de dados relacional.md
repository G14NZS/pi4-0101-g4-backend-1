## Status: aceito

#### Contexto:
O Reservice precisa armazenar dados estruturados e relacionados, como usuários, prestadores, serviços, horários disponíveis, agendamentos, sinais e créditos de clientes. Essas informações possuem relações entre si e exigem consistência, principalmente para evitar registros incoerentes durante operações de agendamento e reagendamento.

##### Decisão:
Adotar o MySQL como sistema gerenciador de banco de dados relacional do Reservice.

##### Alternativas consideradas:
- PostgreSQL: também oferece modelo relacional e recursos adequados ao projeto, mas foi descartado porque a equipe possui maior familiaridade com MySQL e não identificou, no escopo atual, uma necessidade que justificasse a mudança.
- MongoDB: permitiria maior flexibilidade no formato dos documentos, mas foi descartado porque os principais dados do sistema possuem relacionamentos bem definidos e se beneficiam de integridade referencial e estrutura relacional.

##### Consequências:
- Positivas: permite modelar de forma estruturada as relações entre prestadores, clientes, serviços e agendamentos; oferece integridade referencial; utiliza uma tecnologia conhecida pela equipe; facilita consultas relacionadas aos horários e reservas.
- Negativas: exige definição prévia e manutenção do esquema relacional; alterações significativas na estrutura dos dados podem exigir migrações; a prevenção de agendamentos sobrepostos não é garantida apenas pelo banco e também deverá ser tratada pelas regras da aplicação e pelo controle das operações.
Essa última consequência é importante. Eu não escreveria que “MySQL evita agendamentos sobrepostos”. O banco ajuda na consistência, mas a lógica do sistema precisa garantir isso.