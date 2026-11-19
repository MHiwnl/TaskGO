import QRCode from 'qrcode';

/**
 * Gera o payload Pix (copia e cola) no formato EMV, com CRC16-CCITT.
 * TaskGO: a "chave" é o txid da simulação, então nenhum app de banco consegue pagar este código de verdade.
 */
const campo = (id, valor) => id + String(valor.length).padStart(2, '0') + valor;
export function generatePixPayload(txid, valor, descricao = 'Cobranca Pix') {
  const conta = campo('00', 'BR.GOV.BCB.PIX') + campo('01', txid);
  const semCrc =
    campo('00', '01') +
    campo('01', '12') +
    campo('26', conta) +
    campo('52', '0000') +
    campo('53', '986') +
    campo('54', valor.toFixed(2)) +
    campo('58', 'BR') +
    campo('59', process.env.PIX_NOME_RECEBEDOR || 'TASKGO SIMULADO') +
    campo('60', process.env.PIX_CIDADE || 'RECIFE') +
    campo('62', campo('05', txid.slice(0, 25))) +
    '6304';
  return semCrc + crc16(semCrc);
}

function crc16(texto) {
  let crc = 0xFFFF;
  for (const byte of Buffer.from(texto, 'utf8')) {
    crc ^= byte << 8;
    for (let i = 0; i < 8; i++) crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

/**
 * Gera o QR Code em base64
 */
export async function generateQRCode(pixPayload) {
  try {
    const qrCodeDataUrl = await QRCode.toDataURL(pixPayload, {
      errorCorrectionLevel: 'M',
      type: 'image/png',
      width: 300,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#FFFFFF'
      }
    });

    return qrCodeDataUrl;
  } catch (error) {
    console.error('Erro ao gerar QR Code:', error);
    throw new Error('Falha ao gerar QR Code');
  }
}

/**
 * Gera dados completos da cobrança Pix (payload + QR)
 */
export async function generatePixCobranca(txid, valor, descricao) {
  const pixPayload = generatePixPayload(txid, valor, descricao);
  const qrCodeBase64 = await generateQRCode(pixPayload);

  return {
    pixCopiaECola: pixPayload,
    qrCodeBase64: qrCodeBase64
  };
}

/**
 * Valida formato de CPF/CNPJ (simplificado)
 */
export function validateCpfCnpj(value) {
  const cleaned = value.replace(/\D/g, '');
  return cleaned.length === 11 || cleaned.length === 14;
}

/**
 * Formata valor para moeda brasileira
 */
export function formatCurrency(value) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(value);
}
