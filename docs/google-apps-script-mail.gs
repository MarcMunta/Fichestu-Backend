function doPost(e) {
  try {
    const secret = PropertiesService.getScriptProperties().getProperty('FICHESTU_MAIL_SECRET');
    const body = JSON.parse(e.postData.contents || '{}');

    if (!secret || body.secret !== secret) {
      return json({ success: false, message: 'Unauthorized' });
    }

    const to = String(body.to || '').trim();
    const subject = String(body.subject || '').trim();
    const text = String(body.text || '').trim();

    if (!to || !subject || !text) {
      return json({ success: false, message: 'Missing to, subject, or text' });
    }

    GmailApp.sendEmail(to, subject, text, { name: 'Fichestu' });
    return json({ success: true });
  } catch (error) {
    return json({ success: false, message: String(error) });
  }
}

function json(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
