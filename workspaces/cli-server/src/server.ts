import {IPathMapping} from '@useoptic/cli-config';
import {EventEmitter} from 'events';
import express from 'express';
import getPort from 'get-port';
import bodyParser from 'body-parser';
import * as http from 'http';
import * as path from 'path';
import {CapturesHelpers, ExampleRequestsHelpers, makeRouter} from './routers/spec-router';

export interface ICliServerConfig {
  jwtSecret: string
}

export interface IOpticExpressRequestAdditions {
  paths: IPathMapping,
  capturesHelpers: CapturesHelpers
  exampleRequestsHelpers: ExampleRequestsHelpers
}

declare global {
  namespace Express {
    export interface Request {
      optic: IOpticExpressRequestAdditions
    }
  }
}

export interface ICliServerSession {
  id: string
  path: string
}

export const shutdownRequested = 'cli-server:shutdown-requested';

class CliServer {
  private server!: http.Server;
  public events: EventEmitter = new EventEmitter();

  constructor(private config: ICliServerConfig) {
  }

  addUiServer(app: express.Application) {
    const resourceRoot = path.resolve(__dirname, '../../../resources');
    const reactRoot = path.join(resourceRoot, 'react');
    const indexHtmlPath = path.join(reactRoot, 'index.html');
    app.use(express.static(reactRoot));
    app.get('*', (req, res) => {
      res.sendFile(indexHtmlPath);
    });
  }

  makeServer() {
    const app = express();

    const sessions: ICliServerSession[] = [];

    // @REFACTOR sessionsRouter
    app.get('/api/sessions', (req, res: express.Response) => {
      res.json({
        sessions
      });
    });
    app.post('/api/sessions', bodyParser.json({limit: '1kb'}), async (req, res: express.Response) => {
      const {path} = req.body;

      const existingSession = sessions.find(x => x.path === path);
      if (existingSession) {
        return res.json({
          session: {
            id: existingSession.id,
            path: existingSession.path
          }
        });
      }

      const sessionId = (sessions.length + 1).toString();
      const session: ICliServerSession = {
        id: sessionId,
        path
      };
      sessions.push(session);

      return res.json({
        session: {
          id: sessionId
        }
      });
    });
    // @REFACTOR adminRouter
    app.post('/admin-api/commands', bodyParser.json({limit: '1kb'}), async (req, res) => {
      const {body} = req;

      if (body.type === 'shutdown') {
        res.sendStatus(204);
        this.events.emit(shutdownRequested);
        return;
      }

      res.sendStatus(400);
    });

    // specRouter
    const specRouter = makeRouter(sessions);
    app.use('/api/specs/:specId', specRouter);

    return app;
  }

  async start(): Promise<{ port: number }> {
    const app = this.makeServer();
    const port = await getPort({
      port: [34444]
    });
    return new Promise((resolve, reject) => {
      this.server = app.listen(port, () => {
        resolve({
          port
        });
      });
    });
  }

  async stop() {
    if (this.server) {
      await new Promise((resolve) => {
        this.server.close((err) => {
          if (err) {
            console.error(err);
          }
          resolve();
        });
      });
    }
  }
}

export {
  CliServer
};