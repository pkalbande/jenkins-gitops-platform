const express = require('express');
const app = express();
const port = process.env.PORT || 3000;

app.get('/', (req, res) => {
  res.json({
    message: 'Hello from App1 Node.js!',
    version: process.env.APP_VERSION || '1.0.0',
    environment: process.env.ENVIRONMENT || 'development',
    timestamp: new Date().toISOString()
  });
});

app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    uptime: process.uptime()
  });
});

app.get('/ready', (req, res) => {
  res.status(200).json({
    status: 'ready'
  });
});

app.listen(port, () => {
  console.log(`App1 listening on port ${port}`);
});
