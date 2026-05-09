import dotenv from 'dotenv';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import * as appModule from './app.js';
import { startScheduler } from './jobs/scheduler.js';

dotenv.config();
const app = appModule.default;

const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
  startScheduler();
});
