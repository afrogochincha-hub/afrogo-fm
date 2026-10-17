FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package.json ./
COPY server.js ./
COPY genre-classifier.js ./
RUN mkdir -p /data && chown -R node:node /data /app
USER node
EXPOSE 8787
CMD ["node", "server.js"]
