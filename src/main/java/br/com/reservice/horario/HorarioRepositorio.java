package br.com.reservice.horario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/** Acesso à tabela {@code horario_atendimento} (CT16). */
public class HorarioRepositorio {

    /** Faixas da grade do profissional num dia da semana, usando a conexão (e a transação) de quem chama. */
    public List<HorarioAtendimento> listarDoDia(Connection c, int profissionalId, DayOfWeek dia) throws SQLException {
        String sql = "SELECT id, profissional_id, dia_semana, hora_inicio, hora_fim FROM horario_atendimento"
                + " WHERE profissional_id = ? AND dia_semana = ? ORDER BY hora_inicio";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, profissionalId);
            ps.setString(2, HorarioAtendimento.diaSemana(dia));
            try (ResultSet rs = ps.executeQuery()) {
                List<HorarioAtendimento> faixas = new ArrayList<>();
                while (rs.next()) {
                    faixas.add(new HorarioAtendimento(
                            rs.getInt("id"),
                            rs.getInt("profissional_id"),
                            rs.getString("dia_semana"),
                            rs.getString("hora_inicio"),
                            rs.getString("hora_fim")));
                }
                return faixas;
            }
        }
    }
}
