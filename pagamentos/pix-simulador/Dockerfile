FROM node:20-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY src ./src
COPY public ./public
RUN mkdir -p data logs && chown -R node:node /app
USER node
ENV PORT=3100
EXPOSE 3100
CMD ["node", "src/server.js"]
