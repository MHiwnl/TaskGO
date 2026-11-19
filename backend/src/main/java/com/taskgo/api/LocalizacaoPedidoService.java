package com.taskgo.api;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalizacaoPedidoService {
    private static final long MAX_AGE_MS=120_000, FUTURE_TOLERANCE_MS=30_000, MIN_INTERVAL_MS=10_000;
    private final PedidoContratadoRepository pedidos;
    private final LocalizacaoPedidoRepository localizacoes;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalizacaoPedidoService(PedidoContratadoRepository pedidos, LocalizacaoPedidoRepository localizacoes) {
        this(pedidos, localizacoes, Clock.systemUTC());
    }
    LocalizacaoPedidoService(PedidoContratadoRepository pedidos, LocalizacaoPedidoRepository localizacoes, Clock clock) {
        this.pedidos=pedidos; this.localizacoes=localizacoes; this.clock=clock;
    }

    @Transactional
    public LocalizacaoDto registrar(String pedidoId, TaskgoPrincipal principal, LocalizacaoRequest request) {
        Pedido pedido=pedido(pedidoId);
        if (!(("profissional".equalsIgnoreCase(principal.tipo()) || AjudaRapida.eh(pedido)) && igual(principal.email(),pedido.getProEmail())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente o profissional deste pedido pode compartilhar a localização.");
        exigirEmRota(pedido);
        if (request==null || !Double.isFinite(request.latitude()) || request.latitude() < -90 || request.latitude() > 90
                || !Double.isFinite(request.longitude()) || request.longitude() < -180 || request.longitude() > 180
                || !Double.isFinite(request.accuracy()) || request.accuracy() <= 0 || request.accuracy() > 10_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Coordenadas ou precisão inválidas.");
        long now=clock.millis(), measured=request.measuredAt();
        if (measured <= 0 || measured < now-MAX_AGE_MS || measured > now+FUTURE_TOLERANCE_MS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A medição de localização está desatualizada ou tem horário inválido.");
        Optional<LocalizacaoPedido> previous=localizacoes.findById(pedidoId);
        if(previous.isPresent() && (measured <= previous.get().getMeasuredAt() || now-previous.get().getReceivedAt() < MIN_INTERVAL_MS))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Aguarde antes de enviar outra posição.");
        return LocalizacaoDto.from(localizacoes.save(new LocalizacaoPedido(pedidoId,request.latitude(),request.longitude(),request.accuracy(),measured,now)));
    }

    @Transactional(readOnly=true)
    public Optional<LocalizacaoDto> obter(String pedidoId, TaskgoPrincipal principal) {
        Pedido pedido=pedido(pedidoId);
        if(!igual(principal.email(),pedido.getProEmail()) && !igual(principal.email(),pedido.getClientEmail()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Este pedido não pertence a esta conta.");
        exigirEmRota(pedido);
        return localizacoes.findById(pedidoId).map(LocalizacaoDto::from);
    }

    @Transactional
    public void encerrar(String pedidoId, TaskgoPrincipal principal) {
        Pedido pedido=pedido(pedidoId);
        if(!(("profissional".equalsIgnoreCase(principal.tipo()) || AjudaRapida.eh(pedido)) && igual(principal.email(),pedido.getProEmail())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Somente o profissional deste pedido pode encerrar o compartilhamento.");
        localizacoes.deleteById(pedidoId);
    }

    @Transactional
    public void limparSeEncerrado(Pedido pedido) {
        if(pedido!=null && !"a_caminho".equalsIgnoreCase(pedido.getStatus())) localizacoes.deleteById(pedido.getId());
    }

    /* Chegada só vale perto do ponto que o cliente marcou no mapa: a última posição enviada
       pelo profissional precisa ser recente, razoavelmente precisa e estar a até 150 m. */
    public static final double RAIO_CHEGADA_M=150, PRECISAO_MAXIMA_M=100;
    @Transactional(readOnly=true)
    /* Devolve uma observação quando a chegada foi aceita com localização imprecisa (computador, Wi-Fi), ou null. */
    public String exigirPertoDoEndereco(Pedido pedido) {
        Double lat=numero(pedido.getExtra().get("lat")), lng=numero(pedido.getExtra().get("lng"));
        if (lat==null || lng==null) return null; /* pedido antigo, sem ponto marcado: vale só o código */
        LocalizacaoPedido local=localizacoes.findById(pedido.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,
                "Para informar a chegada, compartilhe sua localização neste pedido."));
        if (clock.millis()-local.getReceivedAt() > MAX_AGE_MS)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Sua última posição tem mais de 2 minutos. Compartilhe a localização de novo e tente outra vez.");
        double distancia=distanciaMetros(lat,lng,local.getLatitude(),local.getLongitude());
        /* Posição precisa (GPS do celular): vale a regra dos 150 m. */
        if (local.getAccuracy() <= PRECISAO_MAXIMA_M) {
            if (distancia > RAIO_CHEGADA_M)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Você está a cerca de "+Math.round(distancia)+" m do ponto marcado pelo cliente. A chegada só é aceita a menos de 150 m.");
            return null;
        }
        /* Posição imprecisa (computador, Wi-Fi, ambiente fechado): só recusa quando nem a margem de erro alcança o endereço.
           Aceita com observação; o código de 4 números entregue pelo cliente continua sendo a prova de que o profissional está lá. */
        if (distancia - local.getAccuracy() > RAIO_CHEGADA_M)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Você está longe do ponto marcado pelo cliente (cerca de "+Math.round(distancia)+" m, com margem de "+Math.round(local.getAccuracy())+" m).");
        return "Chegada com localização imprecisa (margem de cerca de "+Math.round(local.getAccuracy())+" m); confirmada depois pelo código do cliente";
    }
    static double distanciaMetros(double lat1,double lng1,double lat2,double lng2) {
        double r=6_371_000, dLat=Math.toRadians(lat2-lat1), dLng=Math.toRadians(lng2-lng1);
        double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLng/2)*Math.sin(dLng/2);
        return 2*r*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }
    private static Double numero(Object v){return v instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : null;}

    private Pedido pedido(String id){return pedidos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido não encontrado."));}
    private void exigirEmRota(Pedido pedido){if(!"a_caminho".equalsIgnoreCase(pedido.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"O rastreamento está disponível somente enquanto o pedido está a caminho.");}
    private boolean igual(String a,String b){return a!=null&&b!=null&&a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));}

    public record LocalizacaoRequest(double latitude,double longitude,double accuracy,long measuredAt) {}
    public record LocalizacaoDto(String pedidoId,double latitude,double longitude,double accuracy,long measuredAt,long receivedAt) {
        static LocalizacaoDto from(LocalizacaoPedido p){return new LocalizacaoDto(p.getPedidoId(),p.getLatitude(),p.getLongitude(),p.getAccuracy(),p.getMeasuredAt(),p.getReceivedAt());}
    }
}
